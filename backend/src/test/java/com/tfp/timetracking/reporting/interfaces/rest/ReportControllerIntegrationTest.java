package com.tfp.timetracking.reporting.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ReportControllerIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void employeeReadsTheirOwnDailySummary() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("emp-summary");
        UUID employeeId = tenant.employee().userId();

        insertWorkday(
                tenant.tenantId(),
                employeeId,
                "CLOSED",
                Instant.parse("2026-02-10T08:00:00Z"),
                Instant.parse("2026-02-10T17:00:00Z"),
                Instant.parse("2026-02-10T12:00:00Z"),
                Instant.parse("2026-02-10T12:30:00Z"));
        attachEvaluation(
                tenant.tenantId(),
                employeeId,
                Instant.parse("2026-02-10T08:00:00Z"),
                Duration.ofHours(8),
                Duration.ofHours(8).plusMinutes(30),
                Duration.ofHours(8).plusMinutes(15),
                Duration.ofMinutes(15),
                Duration.ZERO);

        mockMvc.perform(get("/api/v1/reports/employees/{employeeId}/summary", employeeId)
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].worked").value("PT8H30M"))
                .andExpect(jsonPath("$[0].paused").value("PT30M"))
                .andExpect(jsonPath("$[0].expected").value("PT8H"))
                .andExpect(jsonPath("$[0].effectiveWorked").value("PT8H15M"))
                .andExpect(jsonPath("$[0].overtime").value("PT15M"))
                .andExpect(jsonPath("$[0].deviation").value("PT0S"))
                .andExpect(jsonPath("$[0].evaluatedWorkdayCount").value(1))
                .andExpect(jsonPath("$[0].workdayCount").value(1))
                .andExpect(jsonPath("$[0].openWorkdays").value(0));
    }

    @Test
    void employeeCannotReadAnotherEmployeesSummary() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("emp-summary-other");

        mockMvc.perform(get("/api/v1/reports/employees/{employeeId}/summary", tenant.admin().userId())
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminReadsAnyEmployeeSummaryInTheirTenant() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("admin-summary");
        UUID employeeId = tenant.employee().userId();

        insertWorkday(
                tenant.tenantId(),
                employeeId,
                "CLOSED",
                Instant.parse("2026-02-10T08:00:00Z"),
                Instant.parse("2026-02-10T12:00:00Z"),
                null,
                null);

        mockMvc.perform(get("/api/v1/reports/employees/{employeeId}/summary", employeeId)
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].worked").value("PT4H"));
    }

    @Test
    void openWorkdaysAreExcludedFromWorkedTotalButCountedSeparately() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("open-summary");
        UUID employeeId = tenant.employee().userId();
        insertWorkday(tenant.tenantId(), employeeId, "OPEN", Instant.parse("2026-02-10T08:00:00Z"), null, null, null);

        mockMvc.perform(get("/api/v1/reports/employees/{employeeId}/summary", employeeId)
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].worked").value("PT0S"))
                .andExpect(jsonPath("$[0].openWorkdays").value(1));
    }

    @Test
    void tenantSummaryIsOnlyAvailableToAdmins() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("tenant-summary-role");

        mockMvc.perform(get("/api/v1/reports/tenant/summary")
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void tenantSummaryAggregatesByEmployee() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("tenant-summary");
        UUID employeeId = tenant.employee().userId();
        insertWorkday(
                tenant.tenantId(),
                employeeId,
                "CLOSED",
                Instant.parse("2026-02-10T08:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null,
                null);
        attachEvaluation(
                tenant.tenantId(),
                employeeId,
                Instant.parse("2026-02-10T08:00:00Z"),
                Duration.ofHours(2),
                Duration.ofHours(2),
                Duration.ofHours(2),
                Duration.ZERO,
                Duration.ZERO);

        mockMvc.perform(get("/api/v1/reports/tenant/summary")
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(employeeId.toString()))
                .andExpect(jsonPath("$[0].worked").value("PT2H"))
                .andExpect(jsonPath("$[0].expected").value("PT2H"))
                .andExpect(jsonPath("$[0].effectiveWorked").value("PT2H"))
                .andExpect(jsonPath("$[0].overtime").value("PT0S"))
                .andExpect(jsonPath("$[0].deviation").value("PT0S"))
                .andExpect(jsonPath("$[0].evaluatedWorkdayCount").value(1));
    }

    @Test
    void exportsTenantSummaryAsDownloadableCsv() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("tenant-csv");
        UUID employeeId = tenant.employee().userId();
        insertWorkday(
                tenant.tenantId(),
                employeeId,
                "CLOSED",
                Instant.parse("2026-02-10T08:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null,
                null);
        attachEvaluation(
                tenant.tenantId(),
                employeeId,
                Instant.parse("2026-02-10T08:00:00Z"),
                Duration.ofHours(2),
                Duration.ofHours(2),
                Duration.ofHours(2),
                Duration.ZERO,
                Duration.ZERO);

        String csv = mockMvc.perform(get("/api/v1/reports/tenant/export.csv")
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCsvContains(csv, "employeeId,workedSeconds,pausedSeconds,expectedSeconds,effectiveWorkedSeconds,overtimeSeconds,deviationSeconds,workdayCount,adjustedWorkdayCount,openWorkdays,evaluatedWorkdayCount");
        assertCsvContains(csv, employeeId + ",7200,0,7200,7200,0,0,1,0,0,1");
    }

    @Test
    void employeeCannotExportTheCsv() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("tenant-csv-role");

        mockMvc.perform(get("/api/v1/reports/tenant/export.csv")
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-02-28T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnInvertedDateRange() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("bad-range");

        mockMvc.perform(get("/api/v1/reports/tenant/summary")
                        .param("from", "2026-02-28T00:00:00Z")
                        .param("to", "2026-02-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }

    @Test
    void rejectsARangeLongerThanThreeHundredSixtySixDays() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("long-range");

        mockMvc.perform(get("/api/v1/reports/tenant/summary")
                        .param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }

    private void insertWorkday(
            UUID tenantId, UUID employeeId, String status, Instant startedAt, Instant endedAt, Instant breakStart, Instant breakEnd) {
        UUID workdayId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO workday (id, tenant_id, employee_id, status, started_at, ended_at, version, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                workdayId,
                tenantId,
                employeeId,
                status,
                Timestamp.from(startedAt),
                endedAt != null ? Timestamp.from(endedAt) : null,
                0L,
                now,
                now);
        if (breakStart != null) {
            jdbcTemplate.update(
                    "INSERT INTO break_entry (id, workday_id, started_at, ended_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    workdayId,
                    Timestamp.from(breakStart),
                    breakEnd != null ? Timestamp.from(breakEnd) : null);
        }
    }

    private void attachEvaluation(
            UUID tenantId,
            UUID employeeId,
            Instant startedAt,
            Duration expected,
            Duration worked,
            Duration effectiveWorked,
            Duration overtime,
            Duration deviation) {
        UUID workdayId = jdbcTemplate.queryForObject(
                "SELECT id FROM workday WHERE tenant_id = ? AND employee_id = ? AND started_at = ?",
                UUID.class,
                tenantId,
                employeeId,
                Timestamp.from(startedAt));
        jdbcTemplate.update(
                "INSERT INTO workday_evaluation (workday_id, tenant_id, employee_id, expected_minutes, worked_minutes, effective_worked_minutes, paused_minutes, overtime_minutes, deviation_minutes, anomalies, evaluated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                workdayId,
                tenantId,
                employeeId,
                expected.toMinutes(),
                worked.toMinutes(),
                effectiveWorked.toMinutes(),
                0L,
                overtime.toMinutes(),
                deviation.toMinutes(),
                "",
                Timestamp.from(Instant.now()));
    }

    private void assertCsvContains(String csv, String expected) {
        org.assertj.core.api.Assertions.assertThat(csv).contains(expected);
    }

    @TestConfiguration
    static class ReportControllerIntegrationTestConfiguration {

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
