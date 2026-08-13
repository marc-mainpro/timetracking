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
