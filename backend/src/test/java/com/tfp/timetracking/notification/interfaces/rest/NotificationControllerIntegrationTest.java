package com.tfp.timetracking.notification.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
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
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestTenantFactory testTenantFactory;

    @TestConfiguration
    static class NotificationControllerIntegrationTestConfiguration {

        @Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                RegisterTenantUseCase registerTenantUseCase,
                UserRepository userRepository,
                PasswordHasher passwordHasher,
                Clock clock,
                IdGenerator idGenerator) {
            return new TestTenantFactory(
                    mockMvc, objectMapper, registerTenantUseCase, userRepository, passwordHasher, clock, idGenerator);
        }
    }

    @Test
    void listsOnlyOwnNotificationsAndCountsUnread() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("notif-own");
        givenNotification(tenant.tenantId(), tenant.employee().userId(), "Ausencia aprobada");
        givenNotification(tenant.tenantId(), tenant.admin().userId(), "Notificación del admin");

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Ausencia aprobada"))
                // El frontend navega con esta ruta al pulsar la notificación (T170-02).
                .andExpect(jsonPath("$.content[0].actionPath").value("/absences"))
                .andExpect(jsonPath("$.content[0].read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void marksOwnNotificationAsReadAndDropsTheUnreadCount() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("notif-read");
        Notification notification =
                givenNotification(tenant.tenantId(), tenant.employee().userId(), "Corrección aprobada");

        mockMvc.perform(post("/api/v1/notifications/" + notification.id() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void cannotReadANotificationOfAnotherUserInTheSameTenant() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("notif-other-user");
        Notification adminNotification =
                givenNotification(tenant.tenantId(), tenant.admin().userId(), "Del admin");

        // 404 y no 403: responder 403 confirmaria que ese identificador existe.
        mockMvc.perform(post("/api/v1/notifications/" + adminNotification.id() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotSeeNotificationsOfAnotherTenant() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("notif-tenant-a");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("notif-tenant-b");
        Notification foreign =
                givenNotification(tenantB.tenantId(), tenantB.employee().userId(), "De otro tenant");

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(post("/api/v1/notifications/" + foreign.id() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.employee().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotExposeDeliveryDetails() throws Exception {
        // El correo del destinatario y el ultimo error de envio no son asunto
        // del cliente.
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("notif-fields");
        givenNotification(tenant.tenantId(), tenant.employee().userId(), "Ausencia rechazada");

        String body = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("recipientEmail").doesNotContain("lastError").doesNotContain("attempts");
    }

    private Notification givenNotification(UUID tenantId, UUID recipientUserId, String title) {
        return notificationRepository.save(Notification.create(
                tenantId,
                recipientUserId,
                "destinatario@acme.test",
                NotificationType.ABSENCE_APPROVED,
                title,
                "Cuerpo de la notificación.",
                true,
                "/absences",
                Instant.now(),
                UUID::randomUUID));
    }
}
