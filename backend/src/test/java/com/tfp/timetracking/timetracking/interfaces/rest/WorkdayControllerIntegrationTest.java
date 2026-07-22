package com.tfp.timetracking.timetracking.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class WorkdayControllerIntegrationTest {

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
    void completesFullWorkdayFlowOverHttp() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("flow");

        mockMvc.perform(post("/api/v1/workdays/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("/api/v1/workdays/")))
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/v1/workdays/current/breaks/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_BREAK"))
                .andExpect(jsonPath("$.breaks.length()").value(1));

        mockMvc.perform(post("/api/v1/workdays/current/breaks/end").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.breaks[0].endedAt").isString());

        String closedResponse = mockMvc.perform(post("/api/v1/workdays/current/end").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(get("/api/v1/workdays/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/workdays").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void returns409ForInvalidTransitions() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("conflict");

        mockMvc.perform(post("/api/v1/workdays/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/workdays/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WORKDAY_ALREADY_OPEN"));

        mockMvc.perform(post("/api/v1/workdays/current/breaks/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workdays/current/breaks/start").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BREAK_ALREADY_OPEN"));

        mockMvc.perform(post("/api/v1/workdays/current/end").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WORKDAY_OPEN_BREAK"));

        mockMvc.perform(post("/api/v1/workdays/current/breaks/end").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/workdays/current/breaks/end").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BREAK_NOT_OPEN"));
    }

    @Test
    void employeeCannotAccessAdminEndpoints() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("employee-admin");

        mockMvc.perform(get("/api/v1/admin/workdays").header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("workdays-pagination");

        mockMvc.perform(get("/api/v1/workdays")
                        .param("size", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

        mockMvc.perform(get("/api/v1/admin/workdays")
                        .param("page", "-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }

    @TestConfiguration
    static class WorkdayControllerIntegrationTestConfiguration {

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
