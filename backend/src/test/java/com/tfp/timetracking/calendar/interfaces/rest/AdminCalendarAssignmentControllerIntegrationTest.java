package com.tfp.timetracking.calendar.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Asignaciones de calendario y resolucion del calendario efectivo sobre HTTP
 * real (T70-02/04/05, RF-CAL-006).
 *
 * <p>El caso central es el de la regla "la asignacion mas especifica prevalece"
 * de extremo a extremo, que es el contrato que consumiran turnos y ausencias.
 * Incluye cross-tenant (RT-003) y por rol (RT-004).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCalendarAssignmentControllerIntegrationTest {

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

    /** 2026-03-02 es lunes; 2026-03-07, sabado. */
    private static final String MONDAY = "2026-03-02";

    private static final String SATURDAY = "2026-03-07";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestTenantFactory testTenantFactory;

    private String createCalendar(String token, String name, int mondayMinutes) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("timezone", "Europe/Madrid");
        body.put("validFrom", "2026-01-01");
        body.put("validTo", null);
        body.put("dayRules", List.of(Map.of("dayOfWeek", "MONDAY", "working", true, "expectedMinutes", mondayMinutes)));
        body.put("holidays", List.of());
        body.put("specialDays", List.of());

        String response = mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String assign(String token, String calendarId, String scope, UUID targetId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("calendarId", calendarId);
        body.put("scope", scope);
        body.put("targetId", targetId);

        String response = mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    // --- El contrato de precedencia, de extremo a extremo --------------------

    @Test
    void resolvesTheMostSpecificAssignmentAndFallsBackWhenItIsRemoved() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-precedence");
        String token = tenant.admin().token();
        UUID employeeId = tenant.employee().userId();
        UUID teamId = UUID.randomUUID();

        String tenantCalendar = createCalendar(token, "Tenant", 480);
        String teamCalendar = createCalendar(token, "Equipo", 420);
        String employeeCalendar = createCalendar(token, "Empleado", 300);

        assign(token, tenantCalendar, "TENANT", null);
        String teamAssignment = assign(token, teamCalendar, "TEAM", teamId);
        String employeeAssignment = assign(token, employeeCalendar, "EMPLOYEE", employeeId);

        // Gana el empleado.
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("teamId", teamId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("EMPLOYEE"))
                .andExpect(jsonPath("$.calendarName").value("Empleado"))
                .andExpect(jsonPath("$.working").value(true))
                .andExpect(jsonPath("$.expectedMinutes").value(300))
                .andExpect(jsonPath("$.source").value("WEEKLY_RULE"))
                .andExpect(jsonPath("$.date").value(MONDAY))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"));

        // Retirada la del empleado, gana la del equipo.
        mockMvc.perform(delete("/api/v1/admin/calendar-assignments/" + employeeAssignment)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("teamId", teamId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TEAM"))
                .andExpect(jsonPath("$.expectedMinutes").value(420));

        // Retirada la del equipo, queda la del tenant.
        mockMvc.perform(delete("/api/v1/admin/calendar-assignments/" + teamAssignment)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("teamId", teamId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TENANT"))
                .andExpect(jsonPath("$.expectedMinutes").value(480));
    }

    @Test
    void ignoresTeamAssignmentWhenTheCallerDoesNotSupplyTheTeam() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-noteam");
        String token = tenant.admin().token();
        UUID employeeId = tenant.employee().userId();
        UUID teamId = UUID.randomUUID();

        assign(token, createCalendar(token, "Tenant", 480), "TENANT", null);
        assign(token, createCalendar(token, "Equipo", 420), "TEAM", teamId);

        // Sin teamId el modulo no puede saber a que equipo pertenece el empleado
        // (no hay gestion de equipos, ADR-0013): cae al ambito de tenant.
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TENANT"));
    }

    @Test
    void archivedCalendarStopsWinningAndResolutionFallsBack() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-archived");
        String token = tenant.admin().token();
        UUID employeeId = tenant.employee().userId();

        assign(token, createCalendar(token, "Tenant", 480), "TENANT", null);
        String employeeCalendar = createCalendar(token, "Empleado", 300);
        assign(token, employeeCalendar, "EMPLOYEE", employeeId);

        mockMvc.perform(delete("/api/v1/admin/calendars/" + employeeCalendar)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // La asignacion sigue existiendo, pero su calendario ya no esta disponible.
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("TENANT"));
    }

    @Test
    void reportsNonWorkingDaysThroughTheEffectiveCalendar() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-nonworking");
        String token = tenant.admin().token();
        UUID employeeId = tenant.employee().userId();
        assign(token, createCalendar(token, "Solo lunes", 480), "TENANT", null);

        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("date", SATURDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.working").value(false))
                .andExpect(jsonPath("$.expectedMinutes").value(0))
                .andExpect(jsonPath("$.source").value("WEEKLY_RULE"));
    }

    @Test
    void returns404WhenNoCalendarApplies() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-none");

        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", UUID.randomUUID().toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // --- Alta, listado y errores -------------------------------------------

    @Test
    void listsAssignmentsAndFiltersByCalendar() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-list");
        String token = tenant.admin().token();
        String first = createCalendar(token, "Primero", 480);
        String second = createCalendar(token, "Segundo", 300);
        assign(token, first, "TENANT", null);
        assign(token, second, "EMPLOYEE", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/admin/calendar-assignments")
                        .param("calendarId", first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].scope").value("TENANT"))
                .andExpect(jsonPath("$.content[0].targetId").doesNotExist());
    }

    @Test
    void returns409ForASecondAssignmentOnTheSameScope() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-dup");
        String token = tenant.admin().token();
        String calendarId = createCalendar(token, "Unico", 480);
        assign(token, calendarId, "TENANT", null);

        Map<String, Object> body = new HashMap<>();
        body.put("calendarId", calendarId);
        body.put("scope", "TENANT");
        body.put("targetId", null);

        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CALENDAR_ASSIGNMENT_ALREADY_EXISTS"));
    }

    @Test
    void returns400ForInconsistentScopeAndTarget() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-bad");
        String token = tenant.admin().token();
        String calendarId = createCalendar(token, "Ambitos", 480);

        // TENANT con destinatario.
        Map<String, Object> tenantWithTarget = new HashMap<>();
        tenantWithTarget.put("calendarId", calendarId);
        tenantWithTarget.put("scope", "TENANT");
        tenantWithTarget.put("targetId", UUID.randomUUID());
        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenantWithTarget)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

        // EMPLOYEE sin destinatario.
        Map<String, Object> employeeWithoutTarget = new HashMap<>();
        employeeWithoutTarget.put("calendarId", calendarId);
        employeeWithoutTarget.put("scope", "EMPLOYEE");
        employeeWithoutTarget.put("targetId", null);
        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(employeeWithoutTarget)))
                .andExpect(status().isBadRequest());

        // Ambito inexistente.
        Map<String, Object> unknownScope = new HashMap<>();
        unknownScope.put("calendarId", calendarId);
        unknownScope.put("scope", "DEPARTAMENTO");
        unknownScope.put("targetId", UUID.randomUUID());
        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownScope)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));
    }

    @Test
    void returns404WhenAssigningACalendarThatDoesNotBelongToTheTenant() throws Exception {
        TestTenantFactory.TenantActors owner = testTenantFactory.createTenantActors("assign-owner");
        TestTenantFactory.TenantActors intruder = testTenantFactory.createTenantActors("assign-intruder");
        String foreignCalendar = createCalendar(owner.admin().token(), "Ajeno", 480);

        Map<String, Object> body = new HashMap<>();
        body.put("calendarId", foreignCalendar);
        body.put("scope", "TENANT");
        body.put("targetId", null);

        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruder.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    // --- RT-003: aislamiento entre tenants ---------------------------------

    @Test
    void doesNotLetOneTenantSeeOrRemoveAnotherTenantsAssignment() throws Exception {
        TestTenantFactory.TenantActors owner = testTenantFactory.createTenantActors("assign-cross-a");
        TestTenantFactory.TenantActors intruder = testTenantFactory.createTenantActors("assign-cross-b");
        UUID employeeId = owner.employee().userId();
        String ownerToken = owner.admin().token();
        String assignmentId = assign(ownerToken, createCalendar(ownerToken, "Privado", 480), "EMPLOYEE", employeeId);

        String intruderToken = intruder.admin().token();

        mockMvc.perform(get("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete("/api/v1/admin/calendar-assignments/" + assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // Resolver con el empleado del otro tenant no filtra su calendario.
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // La asignacion del propietario sigue en pie.
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", employeeId.toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("EMPLOYEE"));
    }

    // --- RT-004: autorizacion por rol --------------------------------------

    @Test
    void rejectsEmployeeOnEveryAssignmentEndpointWithForbidden() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("assign-role");
        String token = tenant.admin().token();
        String assignmentId = assign(token, createCalendar(token, "Solo admin", 480), "TENANT", null);
        String employeeToken = tenant.employee().token();

        mockMvc.perform(get("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/calendar-assignments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"calendarId\":\"" + UUID.randomUUID() + "\",\"scope\":\"TENANT\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/calendar-assignments/" + assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/calendar-assignments/effective")
                        .param("employeeId", tenant.employee().userId().toString())
                        .param("date", MONDAY)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/calendar-assignments")).andExpect(status().isUnauthorized());
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class AdminCalendarAssignmentControllerIntegrationTestConfiguration {

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
