package com.tfp.timetracking.tenant.interfaces.rest;

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
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.time.Instant;
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
 * Prueba de integración de la API de plataforma (T50-05, T130-03): listado y
 * ciclo de vida de tenants con rol PLATFORM_ADMIN, bloqueo de acceso del tenant
 * suspendido (RF-TEN-009), auditoría de plataforma y aislamiento de rol.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlatformTenantControllerIntegrationTest {

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
    private TestTenantFactory testTenantFactory;

    @Test
    void platformAdminManagesTenantLifecycleAndSuspensionBlocksAccess() throws Exception {
        String platformToken = createPlatformAdminToken("lifecycle");
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("plat-lc");

        // El tenant aparece en el listado; el tenant de sistema queda excluido.
        mockMvc.perform(get("/api/v1/platform/tenants").header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + PlatformTenant.ID + "')]").isEmpty());

        // Suspender con motivo.
        mockMvc.perform(post("/api/v1/platform/tenants/{id}/suspend", tenant.tenantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SuspendTenantRequest("Impago"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspensionReason").value("Impago"));

        // El usuario del tenant suspendido ya no puede autenticarse (RF-TEN-009).
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AuthLoginRequest(tenant.admin().email(), tenant.admin().password()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("TENANT_INACTIVE"));

        // Reactivar.
        mockMvc.perform(post("/api/v1/platform/tenants/{id}/reactivate", tenant.tenantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // La auditoría de plataforma registra las transiciones.
        mockMvc.perform(get("/api/v1/platform/audit").header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'TENANT_SUSPENDED')]").exists());
    }

    @Test
    void suspendRequiresReasonAndInvalidTransitionConflicts() throws Exception {
        String platformToken = createPlatformAdminToken("rules");
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("plat-rules");

        // Motivo obligatorio.
        mockMvc.perform(post("/api/v1/platform/tenants/{id}/suspend", tenant.tenantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SuspendTenantRequest("   "))))
                .andExpect(status().isBadRequest());

        // Activar un tenant ya ACTIVE es una transición inválida -> 409.
        mockMvc.perform(post("/api/v1/platform/tenants/{id}/activate", tenant.tenantId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_TENANT_TRANSITION"));
    }

    @Test
    void platformAdminCreatesTenantAndItsAdminCanLogIn() throws Exception {
        String platformToken = createPlatformAdminToken("create");
        String ownerEmail = "owner+create+" + Instant.now().toEpochMilli() + "@acme.test";

        String response = mockMvc.perform(post("/api/v1/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest(
                                "Nueva Org", "Europe/Madrid", ownerEmail, "supersecretpwd", "Owner", "Nuevo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tenantId = objectMapper.readTree(response).get("tenantId").asText();

        // El TENANT_ADMIN recién creado puede autenticarse.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.90")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(ownerEmail, "supersecretpwd"))))
                .andExpect(status().isOk());

        // Aparece en el listado de plataforma.
        mockMvc.perform(get("/api/v1/platform/tenants").header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + tenantId + "')]").exists());
    }

    @Test
    void tenantAdminCannotCreateTenants() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("plat-create-forbidden");

        mockMvc.perform(post("/api/v1/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant.admin().token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateTenantRequest(
                                "Otra", "Europe/Madrid", "x@acme.test", "supersecretpwd", "X", "Y"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantAdminCannotAccessPlatformApi() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("plat-forbidden");

        mockMvc.perform(get("/api/v1/platform/tenants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tenant.admin().token())))
                .andExpect(status().isForbidden());
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
    static class PlatformIntegrationTestConfiguration {

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
