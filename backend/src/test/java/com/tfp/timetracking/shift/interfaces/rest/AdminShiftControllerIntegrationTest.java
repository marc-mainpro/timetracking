package com.tfp.timetracking.shift.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminShiftControllerIntegrationTest {

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
    void adminCanCreateListAndAssignShifts() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("shift-admin");
        String token = tenant.admin().token();

        String response = mockMvc.perform(post("/api/v1/admin/shifts/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Turno mañana",
                                "startTime", "08:00:00",
                                "endTime", "16:00:00",
                                "plannedBreakMinutes", 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Turno mañana"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String templateId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/shifts/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Turno mañana"));

        mockMvc.perform(post("/api/v1/admin/shifts/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeId", tenant.employee().userId(),
                                "shiftTemplateId", templateId,
                                "validFrom", "2026-09-01",
                                "validTo", "2026-09-30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(tenant.employee().userId().toString()));
    }

    @Test
    void enforcesRoleAndTenantIsolation() throws Exception {
        TestTenantFactory.TenantActors owner = testTenantFactory.createTenantActors("shift-owner");
        TestTenantFactory.TenantActors intruder = testTenantFactory.createTenantActors("shift-intruder");
        String createResponse = mockMvc.perform(post("/api/v1/admin/shifts/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Turno noche",
                                "startTime", "22:00:00",
                                "endTime", "06:00:00",
                                "plannedBreakMinutes", 20))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String templateId = objectMapper.readTree(createResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/admin/shifts/templates")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.employee().token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/shifts/assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "employeeId", owner.employee().userId(),
                                "shiftTemplateId", templateId,
                                "validFrom", "2026-09-01"))))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class AdminShiftControllerIntegrationTestConfiguration {

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
