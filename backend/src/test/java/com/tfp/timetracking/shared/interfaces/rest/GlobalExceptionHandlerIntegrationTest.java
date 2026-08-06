package com.tfp.timetracking.shared.interfaces.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerIntegrationTest {

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

    @Test
    void unexpectedExceptionsReturnGenericProblemDetailsWithoutInternalLeakage() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("boom");

        mockMvc.perform(get("/api/v1/test/boom")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value("Se ha producido un error interno"))
                .andExpect(jsonPath("$.detail", not(containsString("SELECT"))))
                .andExpect(jsonPath("$.detail", not(containsString("RuntimeException"))));
    }

    @Test
    void aMalformedQueryParameterIsARequestErrorNotAServerError() throws Exception {
        // Detectado por los E2E (T160-01): enviar una fecha donde se espera un
        // instante terminaba en 500, mintiendo sobre la causa y ensuciando las
        // métricas de error del servidor.
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("bad-param");

        mockMvc.perform(get("/api/v1/reports/tenant/summary?from=2020-01-01&to=2999-12-31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail", containsString("from")));
    }

    @Test
    void anUnknownApiPathIsNotFound() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("unknown-path");

        mockMvc.perform(get("/api/v1/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @TestConfiguration
    static class ExceptionHandlerIntegrationTestConfiguration {

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

        @Bean
        BoomController boomController() {
            return new BoomController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class BoomController {

        @GetMapping("/boom")
        String boom() {
            throw new RuntimeException("SELECT * FROM secret_table");
        }
    }
}
