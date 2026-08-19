package com.tfp.timetracking.outbox.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.Role;
import com.tfp.timetracking.identity.domain.User;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.interfaces.rest.AuthLoginRequest;
import com.tfp.timetracking.identity.interfaces.rest.AuthTokenResponse;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Intervencion manual sobre las colas fallidas desde el panel de plataforma.
 *
 * <p>Cubre lo que hace peligrosa a esta API: opera sobre elementos de todos los
 * tenants, asi que el control de rol es la unica barrera que hay.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FailedQueueControllerIntegrationTest {

    private static final String FAILED_OUTBOX = "/api/v1/platform/queues/outbox/failed";

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
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private Clock clock;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private TestTenantFactory testTenantFactory;

    @Test
    void platformAdminListsRetriesAndDiscardsFailedMessages() throws Exception {
        String token = createPlatformAdminToken("queues");
        OutboxMessage failed = failedMessage();

        mockMvc.perform(get(FAILED_OUTBOX).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + failed.id() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + failed.id() + "')].lastError")
                        .value("SMTP caido"));

        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/retry", failed.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(outboxMessageRepository.findById(failed.id()).orElseThrow().status())
                .isEqualTo(OutboxMessageStatus.PENDING);

        OutboxMessage toDiscard = failedMessage();
        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/discard", toDiscard.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DiscardQueueEntryRequest("duplicado"))))
                .andExpect(status().isNoContent());

        // La fila sobrevive al descarte: es la traza que justifica no borrarla.
        OutboxMessage discarded =
                outboxMessageRepository.findById(toDiscard.id()).orElseThrow();
        assertThat(discarded.status()).isEqualTo(OutboxMessageStatus.DISCARDED);
        assertThat(discarded.lastError()).isEqualTo("SMTP caido");

        // Y la decision queda auditada con su motivo.
        mockMvc.perform(get("/api/v1/platform/audit").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'PLATFORM_QUEUE_ENTRY_DISCARDED')]")
                        .exists());
    }

    @Test
    void discardRequiresAReason() throws Exception {
        String token = createPlatformAdminToken("reason");
        OutboxMessage failed = failedMessage();

        // Sin motivo, la auditoria diria quien lo descarto pero no por que.
        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/discard", failed.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DiscardQueueEntryRequest("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actingOnSomethingThatIsNotFailedConflicts() throws Exception {
        String token = createPlatformAdminToken("conflict");
        OutboxMessage pending = outboxMessageRepository.save(newMessage());

        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/retry", pending.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUTBOX_MESSAGE_NOT_FAILED"));
    }

    @Test
    void unknownQueuesAndEntriesAreNotFound() throws Exception {
        String token = createPlatformAdminToken("unknown");

        // Un nombre de cola inventado llega por la URL: es un 404, no un 500.
        mockMvc.perform(get("/api/v1/platform/queues/inventada/failed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/retry", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theQueueOfNotificationsIsServedByTheSameEndpoint() throws Exception {
        String token = createPlatformAdminToken("notifs");

        mockMvc.perform(get("/api/v1/platform/queues/notifications/failed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void aTenantAdminCannotSeeOrTouchOtherTenantsQueues() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("queue-iso");
        OutboxMessage failed = failedMessage();

        // Es toda la barrera que hay: la API no es tenant-scoped.
        mockMvc.perform(get(FAILED_OUTBOX)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant.admin().token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(FAILED_OUTBOX + "/{id}/retry", failed.id())
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant.admin().token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FAILED_OUTBOX)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant.employee().token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(FAILED_OUTBOX)).andExpect(status().isUnauthorized());
    }

    private OutboxMessage failedMessage() {
        OutboxMessage saved = outboxMessageRepository.save(newMessage());
        outboxMessageRepository.markFailed(saved.id(), 8, "SMTP caido");
        return saved;
    }

    private static OutboxMessage newMessage() {
        Instant now = Instant.now();
        return new OutboxMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Workday",
                UUID.randomUUID(),
                "time-tracking.workday-closed.v1",
                1,
                Map.of("foo", "bar"),
                now,
                null,
                8,
                null,
                null,
                OutboxMessageStatus.PENDING,
                now);
    }

    private String createPlatformAdminToken(String seed) throws Exception {
        String email = "platform+" + seed + "+" + Instant.now().toEpochMilli() + "@plataforma.test";
        String password = "platformsecret123";
        User admin = User.create(
                PlatformTenant.ID,
                email,
                passwordHasher.hash(password),
                "Platform",
                "Admin",
                Set.of(Role.PLATFORM_ADMIN),
                clock,
                idGenerator);
        userRepository.save(admin);
        admin.pullDomainEvents();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113." + (Math.abs(seed.hashCode() % 200) + 20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, AuthTokenResponse.class).accessToken();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration
    static class FailedQueueIntegrationTestConfiguration {

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
}
