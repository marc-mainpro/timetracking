package com.tfp.timetracking.tenant.interfaces.rest;

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
import com.tfp.timetracking.notification.application.EmailMessage;
import com.tfp.timetracking.notification.application.EmailSender;
import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.domain.TenantRepository;
import com.tfp.timetracking.tenant.domain.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Revisión de solicitudes desde plataforma (T53-03): la aprobación crea el
 * tenant en {@code PENDING} y su propietario, es idempotente y queda auditada.
 * Cubre también el aislamiento por rol (RT-004): ni {@code TENANT_ADMIN} ni
 * {@code EMPLOYEE} ni un anónimo pueden ver ni decidir sobre las solicitudes de
 * alta, que son datos de otros aspirantes a tenant (RT-003).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "registration.public.enabled=true",
            "registration.verification-url-template=https://app.test/registro/verificar?token=%s"
        })
class PlatformTenantRegistrationControllerIntegrationTest {

    private static final String PUBLIC_ENDPOINT = "/api/v1/public/tenant-registrations";
    private static final String PLATFORM_ENDPOINT = "/api/v1/platform/registrations";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");

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
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private Clock clock;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Autowired
    private CapturingEmailSender emailSender;

    @Autowired
    private TestTenantFactory testTenantFactory;

    /** Crea una solicitud, verifica su correo y devuelve su id. */
    private UUID verifiedRegistration(String seed) throws Exception {
        String email = "owner+" + seed + "+" + Instant.now().toEpochMilli() + "@acme.test";
        emailSender.clear();
        mockMvc.perform(post(PUBLIC_ENDPOINT)
                        .header("X-Forwarded-For", "198.51.100.7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TenantRegistrationRequestBody(
                                "Org " + seed, "Europe/Madrid", "Jane", "Doe", email, "supersecretpwd", true))))
                .andExpect(status().isAccepted());

        publishPendingOutboxMessages.publishBatch();
        Matcher matcher = TOKEN_IN_LINK.matcher(emailSender.lastBody());
        assertThat(matcher.find()).isTrue();

        mockMvc.perform(post(PUBLIC_ENDPOINT + "/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyRegistrationEmailRequest(matcher.group(1)))))
                .andExpect(status().isOk());

        String listed = mockMvc.perform(get(PLATFORM_ENDPOINT)
                        .param("status", "PENDING_REVIEW")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken("list-" + seed))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (var node : objectMapper.readTree(listed).get("content")) {
            if (email.equals(node.get("email").asText())) {
                return UUID.fromString(node.get("id").asText());
            }
        }
        throw new AssertionError("La solicitud de " + email + " no aparece en el listado de plataforma");
    }

    @Test
    void approvingCreatesAPendingTenantAndItsOwnerAndIsIdempotent() throws Exception {
        String token = platformToken("approve");
        UUID registrationId = verifiedRegistration("approve");

        String first = mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.createdTenantId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID tenantId = UUID.fromString(objectMapper.readTree(first).get("createdTenantId").asText());

        // El tenant nace PENDING: aprobar la solicitud no lo pone a operar.
        assertThat(tenantRepository.findById(tenantId))
                .get()
                .satisfies(tenant -> assertThat(tenant.status()).isEqualTo(TenantStatus.PENDING));

        // Segunda aprobación: mismo tenant, no uno nuevo.
        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdTenantId").value(tenantId.toString()));

        // Y queda auditada.
        mockMvc.perform(get("/api/v1/platform/audit").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'TENANT_REGISTRATION_APPROVED')]").exists());
    }

    @Test
    void theOwnerCannotLogInUntilThePlatformActivatesTheTenant() throws Exception {
        String token = platformToken("owner-login");
        UUID registrationId = verifiedRegistration("owner-login");

        String approved = mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String ownerEmail = objectMapper.readTree(approved).get("email").asText();
        UUID tenantId = UUID.fromString(objectMapper.readTree(approved).get("createdTenantId").asText());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.31")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest(ownerEmail, "supersecretpwd"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TENANT_INACTIVE"));

        mockMvc.perform(post("/api/v1/platform/tenants/{id}/activate", tenantId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "198.51.100.32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest(ownerEmail, "supersecretpwd"))))
                .andExpect(status().isOk());
    }

    @Test
    void rejectingRequiresAReasonAndBlocksAnyLaterApproval() throws Exception {
        String token = platformToken("reject");
        UUID registrationId = verifiedRegistration("reject");

        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/reject", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectRegistrationRequest("   "))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/reject", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RejectRegistrationRequest("Dominio desechable"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionReason").value("Dominio desechable"));

        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", registrationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_TENANT_REGISTRATION_TRANSITION"));
    }

    @Test
    void anUnknownRegistrationIsNotFound() throws Exception {
        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken("missing"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnknownStatusFilterIsABadRequest() throws Exception {
        mockMvc.perform(get(PLATFORM_ENDPOINT)
                        .param("status", "INVENTADO")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken("bad-filter"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pagingParametersAreValidated() throws Exception {
        String token = platformToken("paging");

        mockMvc.perform(get(PLATFORM_ENDPOINT)
                        .param("size", "1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PLATFORM_ENDPOINT)
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantUsersCannotSeeOrDecideOnRegistrations() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("reg-forbidden");
        UUID someRegistration = verifiedRegistration("reg-forbidden");

        for (String tenantToken : List.of(tenant.admin().token(), tenant.employee().token())) {
            mockMvc.perform(get(PLATFORM_ENDPOINT).header(HttpHeaders.AUTHORIZATION, bearer(tenantToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", someRegistration)
                            .header(HttpHeaders.AUTHORIZATION, bearer(tenantToken)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/reject", someRegistration)
                            .header(HttpHeaders.AUTHORIZATION, bearer(tenantToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RejectRegistrationRequest("no"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousUsersCannotReachThePlatformRegistrationApi() throws Exception {
        mockMvc.perform(get(PLATFORM_ENDPOINT)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PLATFORM_ENDPOINT + "/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private String platformToken(String seed) throws Exception {
        String email = "platform+reg+" + seed + "+" + Instant.now().toEpochMilli() + "@plataforma.test";
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
                        .header("X-Forwarded-For", "198.51.100." + (Math.abs(seed.hashCode() % 100) + 100))
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

    /** Doble de test del puerto {@link EmailSender} para poder leer el token del correo. */
    static final class CapturingEmailSender implements EmailSender {
        private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

        @Override
        public void send(EmailMessage message) {
            messages.add(message);
        }

        void clear() {
            messages.clear();
        }

        String lastBody() {
            assertThat(messages).isNotEmpty();
            return messages.get(messages.size() - 1).body();
        }
    }

    @TestConfiguration
    static class PlatformRegistrationTestConfiguration {

        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }

        @Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                UserRepository userRepository,
                PasswordHasher passwordHasher,
                Clock clock,
                IdGenerator idGenerator) {
            return new TestTenantFactory(mockMvc, objectMapper, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
