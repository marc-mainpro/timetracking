package com.tfp.timetracking.absence.interfaces.rest;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppAbsenceControllerIntegrationTest {

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
    void employeeCanListTypesRequestListAndCancelOwnAbsence() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("absence-app");
        UUID typeId = insertAbsenceType(tenant.tenantId(), "VAC", true, true);

        mockMvc.perform(get("/api/v1/app/absence-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("VAC"));

        String response = mockMvc.perform(post("/api/v1/app/absences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "absenceTypeId", typeId,
                                "startDate", "2026-08-10",
                                "endDate", "2026-08-12",
                                "reason", "Vacaciones"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String absenceId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/app/absences")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/v1/app/absences/{absenceId}/cancel", absenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void rejectsInactiveAbsenceTypeAndCrossTenantCancellation() throws Exception {
        TestTenantFactory.TenantActors owner = testTenantFactory.createTenantActors("absence-owner");
        TestTenantFactory.TenantActors intruder = testTenantFactory.createTenantActors("absence-intruder");
        UUID inactiveTypeId = insertAbsenceType(owner.tenantId(), "MED", false, false);

        mockMvc.perform(post("/api/v1/app/absences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.employee().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "absenceTypeId", inactiveTypeId,
                                "startDate", "2026-08-10",
                                "endDate", "2026-08-12"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ABSENCE_TYPE_INACTIVE"));

        UUID activeTypeId = insertAbsenceType(owner.tenantId(), "VAC", true, false);
        String response = mockMvc.perform(post("/api/v1/app/absences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.employee().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "absenceTypeId", activeTypeId,
                                "startDate", "2026-08-10",
                                "endDate", "2026-08-12"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String absenceId = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(post("/api/v1/app/absences/{absenceId}/cancel", absenceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.employee().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesBadPayloadAndRoleRestrictions() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("absence-validation");
        UUID typeId = insertAbsenceType(tenant.tenantId(), "VAC", true, false);

        mockMvc.perform(post("/api/v1/app/absences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "absenceTypeId", typeId,
                                "startDate", "2026-08-12",
                                "endDate", "2026-08-10",
                                "reason", "R".repeat(501)))))
                .andExpect(status().isBadRequest());

        // Solicitar es cosa del empleado: el admin no puede hacerlo por él.
        mockMvc.perform(post("/api/v1/app/absences")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "absenceTypeId", typeId,
                                "startDate", "2026-08-10",
                                "endDate", "2026-08-12"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsTheCatalogueOfItsOwnTenantToResolveRequests() throws Exception {
        // La pantalla de resolución traduce el absenceTypeId de cada solicitud a
        // su nombre; sin esto mostraba UUIDs crudos.
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("absence-admin-types");
        TestTenantFactory.TenantActors other = testTenantFactory.createTenantActors("absence-admin-other");
        insertAbsenceType(tenant.tenantId(), "VAC", true, true);
        insertAbsenceType(other.tenantId(), "AJENO", true, true);

        mockMvc.perform(get("/api/v1/app/absence-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("VAC"));
    }

    private UUID insertAbsenceType(UUID tenantId, String code, boolean active, boolean requiresApproval) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO absence_type (id, tenant_id, code, name, requires_approval, allows_attachment, active) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                code,
                code,
                requiresApproval,
                false,
                active);
        return id;
    }

    @TestConfiguration
    static class AppAbsenceControllerIntegrationTestConfiguration {

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
