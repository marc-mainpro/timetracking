package com.tfp.timetracking.timetracking.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AdminHourlyRulesControllerIntegrationTest {

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
    private TestTenantFactory testTenantFactory;

    @Test
    void returnsDefaultsThenPersistsUpdatedRules() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("hourly-rules-crud");
        String token = tenant.admin().token();

        mockMvc.perform(get("/api/v1/admin/hourly-rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyWorkMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.requiredBreakMinutes").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/v1/admin/hourly-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "maxDailyWorkMinutes", 480,
                                "requiredBreakMinutes", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyWorkMinutes").value(480))
                .andExpect(jsonPath("$.requiredBreakMinutes").value(30));

        mockMvc.perform(get("/api/v1/admin/hourly-rules").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyWorkMinutes").value(480))
                .andExpect(jsonPath("$.requiredBreakMinutes").value(30));
    }

    @Test
    void isolatesRulesByTenant() throws Exception {
        TestTenantFactory.TenantActors first = testTenantFactory.createTenantActors("hourly-rules-a");
        TestTenantFactory.TenantActors second = testTenantFactory.createTenantActors("hourly-rules-b");

        mockMvc.perform(put("/api/v1/admin/hourly-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + first.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "maxDailyWorkMinutes", 480,
                                "requiredBreakMinutes", 30))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/hourly-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + second.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxDailyWorkMinutes").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.requiredBreakMinutes").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsEmployeeAndAnonymous() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("hourly-rules-auth");

        mockMvc.perform(get("/api/v1/admin/hourly-rules")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/hourly-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesIncomingPayload() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("hourly-rules-validation");

        mockMvc.perform(put("/api/v1/admin/hourly-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "maxDailyWorkMinutes", 0,
                                "requiredBreakMinutes", -1))))
                .andExpect(status().isBadRequest());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class AdminHourlyRulesControllerIntegrationTestConfiguration {

        @org.springframework.context.annotation.Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                com.tfp.timetracking.identity.domain.UserRepository userRepository,
                com.tfp.timetracking.identity.domain.PasswordHasher passwordHasher,
                com.tfp.timetracking.shared.domain.Clock clock,
                com.tfp.timetracking.shared.domain.IdGenerator idGenerator) {
            return new TestTenantFactory(mockMvc, objectMapper, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
