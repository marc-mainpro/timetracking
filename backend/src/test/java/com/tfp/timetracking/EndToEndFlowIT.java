package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionRequestDto;
import com.tfp.timetracking.corrections.interfaces.rest.CorrectionResolutionRequest;
import com.tfp.timetracking.identity.interfaces.rest.AuthLoginRequest;
import com.tfp.timetracking.identity.interfaces.rest.AuthTokenResponse;
import com.tfp.timetracking.identity.interfaces.rest.CreateEmployeeRequest;
import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantRequest;
import com.tfp.timetracking.tenant.interfaces.rest.RegisterTenantResponse;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class EndToEndFlowIT {

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
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Test
    void fullFlowWorksEndToEndAndTwoTenantsRemainIsolated() throws Exception {
        TenantSession tenantA = registerTenant("e2e-a");
        EmployeeSession employeeA = createEmployee(tenantA, "a");
        TenantSession tenantB = registerTenant("e2e-b");
        EmployeeSession employeeB = createEmployee(tenantB, "b");

        WorkdayFlow workdayA = completeWorkday(employeeA.token(), "2026-01-15T09:05:00Z", "2026-01-15T18:05:00Z");
        WorkdayFlow workdayB = completeWorkday(employeeB.token(), "2026-01-16T08:30:00Z", "2026-01-16T17:00:00Z");

        assertHistoryContainsOnly(employeeA.token(), workdayA.workdayId());
        assertHistoryContainsOnly(employeeB.token(), workdayB.workdayId());

        String correctionA = requestCorrection(employeeA.token(), workdayA.workdayId(), workdayA.proposedChanges());
        approveCorrection(tenantA.adminToken(), correctionA);

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CORRECTION_APPROVED"));

        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .param("action", "CORRECTION_APPROVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/workdays/{workdayId}", workdayB.workdayId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantA.adminToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/corrections/{correctionId}", correctionA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantB.adminToken()))
                .andExpect(status().isNotFound());

        long tenantAOutbox = countOutboxMessages(tenantA.tenantId());
        long tenantBOutbox = countOutboxMessages(tenantB.tenantId());
        assertThat(tenantAOutbox).isGreaterThanOrEqualTo(6);
        assertThat(tenantBOutbox).isGreaterThanOrEqualTo(4);

        int published = publishPendingOutboxMessages.publishBatch();
        while (publishPendingOutboxMessages.publishBatch() > 0) {
            published++;
        }
        assertThat(published).isGreaterThan(0);

        assertThat(countOutboxMessagesByStatus(tenantA.tenantId(), OutboxMessageStatus.PENDING)).isZero();
        assertThat(countOutboxMessagesByStatus(tenantB.tenantId(), OutboxMessageStatus.PENDING)).isZero();
        assertThat(countOutboxMessagesByStatus(tenantA.tenantId(), OutboxMessageStatus.PUBLISHED)).isGreaterThan(0);
        assertThat(countOutboxMessagesByStatus(tenantB.tenantId(), OutboxMessageStatus.PUBLISHED)).isGreaterThan(0);
    }

    private TenantSession registerTenant(String seed) throws Exception {
        long suffix = Instant.now().toEpochMilli() + Math.abs(seed.hashCode());
        String email = "admin+" + seed + "+" + suffix + "@acme.test";
        String password = "supersecretpwd";
        RegisterTenantRequest request =
                new RegisterTenantRequest("Tenant " + seed, "Europe/Madrid", email, password, "Admin", seed);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Forwarded-For", "198.51.100." + (Math.abs(seed.hashCode() % 200) + 20))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        RegisterTenantResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), RegisterTenantResponse.class);
        return new TenantSession(response.tenantId(), response.adminUserId(), email, password, login(email, password));
    }

    private EmployeeSession createEmployee(TenantSession tenant, String seed) throws Exception {
        String email = "employee+" + seed + "+" + tenant.tenantId() + "@acme.test";
        String password = "employeepwd123";
        MvcResult result = mockMvc.perform(post("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateEmployeeRequest(
                                email, password, "Employee", seed.toUpperCase(), Set.of("EMPLOYEE")))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new EmployeeSession(UUID.fromString(body.get("id").asText()), email, password, login(email, password));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthLoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class).accessToken();
    }

    private WorkdayFlow completeWorkday(String employeeToken, String start, String end) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/workdays/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isCreated())
                .andReturn();
        String workdayId = objectMapper.readTree(started.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/workdays/current/breaks/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workdays/current/breaks/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workdays/current/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isOk());

        return new WorkdayFlow(workdayId, new CorrectionRequestDto.ProposedChangesDto(
                Instant.parse(start),
                Instant.parse(end),
                List.of(new CorrectionRequestDto.ProposedBreakDto(
                        Instant.parse("2026-01-15T12:00:00Z"), Instant.parse("2026-01-15T12:30:00Z")))));
    }

    private void assertHistoryContainsOnly(String token, String expectedWorkdayId) throws Exception {
        mockMvc.perform(get("/api/v1/workdays")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedWorkdayId));
    }

    private String requestCorrection(String token, String workdayId, CorrectionRequestDto.ProposedChangesDto proposedChanges)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workdays/{workdayId}/corrections", workdayId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CorrectionRequestDto("Ajuste de jornada", proposedChanges))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void approveCorrection(String adminToken, String correctionId) throws Exception {
        mockMvc.perform(post("/api/v1/corrections/{correctionId}/approve", correctionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CorrectionResolutionRequest("Aprobada en E2E"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private long countOutboxMessages(UUID tenantId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where tenant_id = ?::uuid", Long.class, tenantId.toString());
    }

    private long countOutboxMessagesByStatus(UUID tenantId, OutboxMessageStatus status) {
        return jdbcTemplate.queryForObject(
                "select count(*) from outbox_message where tenant_id = ?::uuid and status = ?",
                Long.class,
                tenantId.toString(),
                status.name());
    }

    private record TenantSession(UUID tenantId, UUID adminUserId, String adminEmail, String adminPassword, String adminToken) {}

    private record EmployeeSession(UUID userId, String email, String password, String token) {}

    private record WorkdayFlow(String workdayId, CorrectionRequestDto.ProposedChangesDto proposedChanges) {}
}
