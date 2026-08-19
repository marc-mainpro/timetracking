package com.tfp.timetracking.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integracion de la cola de envio (T170-02).
 *
 * <p>Cubre la consecuencia menos evidente de que el canal sea un dato de la
 * fila: una notificacion solo in-app nace {@code PENDING} y <b>se queda asi
 * para siempre</b>, porque no hay envio que la mueva de estado. Ni el lote del
 * emisor ni el pendiente del panel deben verla; si la vieran, la cola pareceria
 * atascarse sola con cada anomalia de jornada.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationRepositoryAdapterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("timetracking")
                    .withUsername("timetracking")
                    .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void leavesInAppOnlyNotificationsOutOfTheDeliveryQueue() {
        jdbcTemplate.update("DELETE FROM notification");
        UUID tenantId = insertTenant();
        Notification withEmail = save(tenantId, NotificationType.WORKDAY_ANOMALY_DETECTED, true, "/workdays");
        save(tenantId, NotificationType.TEAM_WORKDAY_ANOMALY, false, "/admin/reports");

        List<Notification> batch = notificationRepository.findPendingForDelivery(50);

        assertThat(batch).extracting(Notification::id).containsExactly(withEmail.id());
        // El panel mide trabajo encolado, no filas en estado PENDING: contar la
        // in-app haria crecer el pendiente indefinidamente sin nada atascado.
        assertThat(notificationRepository.countPendingForDelivery()).isEqualTo(1);
    }

    @Test
    void leavesNotificationsWithoutAnAddressOutOfTheDeliveryQueue() {
        jdbcTemplate.update("DELETE FROM notification");
        UUID tenantId = insertTenant();
        save(tenantId, NotificationType.ABSENCE_APPROVED, true, null, null);

        assertThat(notificationRepository.findPendingForDelivery(50)).isEmpty();
        assertThat(notificationRepository.countPendingForDelivery()).isZero();
    }

    @Test
    void findsFailedNotificationsAcrossTenantsForThePlatformPanel() {
        jdbcTemplate.update("DELETE FROM notification");
        UUID firstTenant = insertTenant();
        UUID secondTenant = insertTenant();
        Notification older = failed(firstTenant);
        Notification newer = failed(secondTenant);
        jdbcTemplate.update(
                "UPDATE notification SET created_at = NOW() - interval '1 day' WHERE id = ?", older.id());

        var firstPage = notificationRepository.findByStatus(
                com.tfp.timetracking.notification.domain.NotificationStatus.FAILED, 0, 1);

        // Cruza tenants a proposito: el panel vigila el envio del sistema
        // entero, y lo mas antiguo es lo que lleva mas tiempo sin atenderse.
        assertThat(firstPage.content()).extracting(Notification::id).containsExactly(older.id());
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(newer.tenantId()).isNotEqualTo(older.tenantId());
    }

    @Test
    void findsANotificationByIdWithoutATenantForPlatformOperations() {
        UUID tenantId = insertTenant();
        Notification failed = failed(tenantId);

        assertThat(notificationRepository.findByIdForPlatform(failed.id()))
                .get()
                .extracting(Notification::id)
                .isEqualTo(failed.id());
        assertThat(notificationRepository.findByIdForPlatform(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aDiscardedNotificationIsPersistedAndStopsCountingAsFailed() {
        jdbcTemplate.update("DELETE FROM notification");
        UUID tenantId = insertTenant();
        Notification failed = failed(tenantId);

        failed.discardDelivery();
        notificationRepository.save(failed);

        // El CHECK de la V27 admite el estado nuevo, y la incidencia desaparece
        // del panel sin que la fila se pierda.
        assertThat(notificationRepository.countByStatus(
                        com.tfp.timetracking.notification.domain.NotificationStatus.FAILED))
                .isZero();
        assertThat(notificationRepository.findByIdForPlatform(failed.id()))
                .get()
                .extracting(Notification::lastError)
                .isEqualTo("SMTP caido");
    }

    @Test
    void aDiscardedNotificationIsStillVisibleToItsRecipient() {
        jdbcTemplate.update("DELETE FROM notification");
        UUID tenantId = insertTenant();
        Notification failed = failed(tenantId);

        failed.discardDelivery();
        notificationRepository.save(failed);

        // Descartar renuncia al correo, no al aviso: quien lo esperaba debe
        // seguir viendolo en la aplicacion, y contando como no leido.
        assertThat(notificationRepository
                        .findByRecipient(tenantId, failed.recipientUserId(), 0, 20)
                        .content())
                .extracting(Notification::id)
                .containsExactly(failed.id());
        assertThat(notificationRepository.countUnreadByRecipient(tenantId, failed.recipientUserId()))
                .isEqualTo(1);
    }

    /** Una notificacion que ya agoto sus reintentos de envio, ya persistida. */
    private Notification failed(UUID tenantId) {
        Notification notification = save(tenantId, NotificationType.ABSENCE_APPROVED, true, "/absences");
        notification.markAttemptFailed("SMTP caido", 1, Instant.now());
        return notificationRepository.save(notification);
    }

    private Notification save(UUID tenantId, NotificationType type, boolean emailRequired, String actionPath) {
        return save(tenantId, type, emailRequired, actionPath, "destinatario@acme.test");
    }

    private Notification save(
            UUID tenantId, NotificationType type, boolean emailRequired, String actionPath, String email) {
        return notificationRepository.save(Notification.create(
                tenantId,
                UUID.randomUUID(),
                email,
                type,
                "Título",
                "Cuerpo de la notificación.",
                emailRequired,
                actionPath,
                Instant.now(),
                UUID::randomUUID));
    }

    private UUID insertTenant() {
        UUID id = UUID.randomUUID();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, status, timezone, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                id, "Tenant " + id, "ACTIVE", "Europe/Madrid", now, now);
        return id;
    }
}
