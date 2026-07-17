package com.tfp.timetracking.audit.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionRejectRequest;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionRequestDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class AuditEventControllerIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestTenantFactory testTenantFactory;

    @Test
    void approvingCorrectionCreatesAuditEventAndAdminListsOnlyOwnTenantAudit() throws Exception {
        TestTenantFactory.TenantActors tenantA = testTenantFactory.createTenantActors("audit-a");
        TestTenantFactory.TenantActors tenantB = testTenantFactory.createTenantActors("audit-b");
        String workdayId = startAndCloseWorkday(tenantA.employee().token());
        String correctionId = requestCorrection(tenantA.employee().token(), workdayId);

        mockMvc.perform(post("/api/v1/corrections/{correctionId}/reject", correctionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token())
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CorrectionRejectRequest("No procede"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CORRECTION_REJECTED"));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private String startAndCloseWorkday(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workdays/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String workdayId = objectMapper.readTree(response).get("id").asText();
        mockMvc.perform(post("/api/v1/workdays/current/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return workdayId;
    }

    private String requestCorrection(String token, String workdayId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workdays/{workdayId}/corrections", workdayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CorrectionRequestDto(
                                "Ajuste",
                                new CorrectionRequestDto.ProposedChangesDto(
                                        Instant.parse("2026-01-15T09:05:00Z"),
                                        Instant.parse("2026-01-15T18:05:00Z"),
                                        List.of(new CorrectionRequestDto.ProposedBreakDto(
                                                Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z"))))))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @TestConfiguration
    static class AuditIntegrationTestConfiguration {

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
