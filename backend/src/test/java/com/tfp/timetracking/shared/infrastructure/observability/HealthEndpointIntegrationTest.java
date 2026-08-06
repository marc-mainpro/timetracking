package com.tfp.timetracking.shared.infrastructure.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
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

/**
 * T140-03 / RO-001: los health checks de aplicacion, PostgreSQL, outbox y
 * correo, y la tension entre "la sonda tiene que ser publica" y "el detalle no
 * puede serlo".
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIntegrationTest {

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
    private TestTenantFactory testTenantFactory;

    /** La sonda del contenedor: publica, 200 y sin detalles. */
    @Test
    void publicProbeExposesOnlyTheAggregatedStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * El grupo operativo esta en la misma ruta publica {@code /actuator/health/**},
     * pero {@code show-details: when-authorized} hace que un anonimo no vea nada
     * mas que el estado: umbrales, host SMTP y tamano del backlog no son
     * informacion publica.
     */
    @Test
    void operationsGroupHidesDetailsFromAnonymousCallers() throws Exception {
        mockMvc.perform(get("/actuator/health/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void operationsGroupShowsEveryCheckToAnAuthenticatedAdmin() throws Exception {
        String adminToken = testTenantFactory.createTenantActors("health-admin").admin().token();

        mockMvc.perform(get("/actuator/health/operations").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // Aplicacion.
                .andExpect(jsonPath("$.components.ping.status").value("UP"))
                // PostgreSQL.
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                // Outbox: sin backlog ni mensajes fallidos en una base limpia.
                .andExpect(jsonPath("$.components.outbox.status").value("UP"))
                .andExpect(jsonPath("$.components.outbox.details.failed").value(0))
                // Correo: deshabilitado (mail.enabled=false), no caido, y el
                // agregado sigue siendo UP.
                .andExpect(jsonPath("$.components.mail.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.components.mail.details.enabled").value(false));
    }

    /** Un empleado no es personal de operaciones: ve el estado, no el detalle. */
    @Test
    void operationsGroupHidesDetailsFromNonAdminRoles() throws Exception {
        String employeeToken = testTenantFactory.createTenantActors("health-employee").employee().token();

        mockMvc.perform(get("/actuator/health/operations").header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @TestConfiguration
    static class HealthIntegrationTestConfiguration {

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
