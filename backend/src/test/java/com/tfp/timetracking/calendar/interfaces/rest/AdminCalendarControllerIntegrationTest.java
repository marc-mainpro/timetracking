package com.tfp.timetracking.calendar.interfaces.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import com.tfp.timetracking.tenant.application.RegisterTenantUseCase;
import java.util.Map;
import javax.sql.DataSource;
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
 * API de administracion de calendarios sobre HTTP real (T70-05).
 *
 * <p>Cubre la Definition of Done de la V2: flujo completo, <b>cross-tenant</b>
 * (RT-003: un tenant no puede leer ni modificar el calendario de otro, y recibe
 * 404 en vez de 403 para no filtrar su existencia) y <b>por rol</b> (RT-004: un
 * {@code EMPLOYEE} recibe 403 en todos los endpoints de administracion).
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCalendarControllerIntegrationTest {

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

    @Autowired
    private DataSource dataSource;

    private Map<String, Object> calendarBody(String name) {
        return Map.of(
                "name", name,
                "timezone", "Europe/Madrid",
                "validFrom", "2026-01-01",
                "validTo", "2026-12-31",
                "dayRules",
                        java.util.List.of(
                                Map.of("dayOfWeek", "MONDAY", "working", true, "expectedMinutes", 480),
                                Map.of("dayOfWeek", "SATURDAY", "working", false, "expectedMinutes", 0)),
                "holidays", java.util.List.of(Map.of("date", "2026-01-06", "name", "Reyes")),
                "specialDays",
                        java.util.List.of(
                                Map.of("date", "2026-12-24", "name", "Intensiva", "expectedMinutes", 300)));
    }

    private String createCalendar(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody(name))))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION, org.hamcrest.Matchers.containsString("/api/v1/admin/calendars/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    @Test
    void completesFullCalendarLifecycleOverHttp() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-crud");
        String token = tenant.admin().token();

        String calendarId = createCalendar(token, "General");

        mockMvc.perform(get("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General"))
                .andExpect(jsonPath("$.timezone").value("Europe/Madrid"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.validFrom").value("2026-01-01"))
                .andExpect(jsonPath("$.validTo").value("2026-12-31"))
                .andExpect(jsonPath("$.dayRules.length()").value(2))
                .andExpect(jsonPath("$.holidays[0].date").value("2026-01-06"))
                .andExpect(jsonPath("$.specialDays[0].expectedMinutes").value(300));

        mockMvc.perform(get("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].holidayCount").value(1))
                .andExpect(jsonPath("$.content[0].specialDayCount").value(1));

        Map<String, Object> updated = new java.util.HashMap<>(calendarBody("General revisado"));
        updated.put("holidays", java.util.List.of());
        updated.put("validTo", null);
        mockMvc.perform(put("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General revisado"))
                .andExpect(jsonPath("$.holidays.length()").value(0))
                .andExpect(jsonPath("$.validTo").doesNotExist());

        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // El archivado es logico: el calendario sigue consultable.
        mockMvc.perform(get("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/v1/admin/calendars?status=ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(get("/api/v1/admin/calendars?status=ACTIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // --- RT-003: aislamiento entre tenants -------------------------------

    @Test
    void doesNotLetOneTenantReadOrModifyAnotherTenantsCalendar() throws Exception {
        TestTenantFactory.TenantActors owner = testTenantFactory.createTenantActors("cal-owner");
        TestTenantFactory.TenantActors intruder = testTenantFactory.createTenantActors("cal-intruder");
        String calendarId = createCalendar(owner.admin().token(), "Privado");
        String intruderToken = intruder.admin().token();

        // 404 y no 403: un 403 confirmaria que el calendario existe.
        mockMvc.perform(get("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Secuestrado"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isNotFound());

        // El listado del intruso no ve nada del propietario.
        mockMvc.perform(get("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + intruderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));

        // Y el calendario del propietario sigue intacto.
        mockMvc.perform(get("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.admin().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Privado"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void allowsTheSameCalendarNameInDifferentTenants() throws Exception {
        TestTenantFactory.TenantActors first = testTenantFactory.createTenantActors("cal-name-a");
        TestTenantFactory.TenantActors second = testTenantFactory.createTenantActors("cal-name-b");

        createCalendar(first.admin().token(), "Calendario comun");
        createCalendar(second.admin().token(), "Calendario comun");
    }

    // --- RT-004: autorizacion por rol ------------------------------------

    @Test
    void rejectsEmployeeOnEveryAdminEndpointWithForbidden() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-role");
        String calendarId = createCalendar(tenant.admin().token(), "Solo admin");
        String employeeToken = tenant.employee().token();

        mockMvc.perform(get("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Prohibido"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Prohibido"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/calendars")).andExpect(status().isUnauthorized());
    }

    // --- Errores de negocio y de validacion --------------------------------

    @Test
    void returns409WithStableErrorCodeForDuplicateName() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-dup");
        String token = tenant.admin().token();
        createCalendar(token, "Repetido");

        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Repetido"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CALENDAR_NAME_ALREADY_EXISTS"));
    }

    @Test
    void returns409WhenEditingAnArchivedCalendar() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-archived");
        String token = tenant.admin().token();
        String calendarId = createCalendar(token, "Para archivar");

        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Para archivar"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CALENDAR_ARCHIVED"));

        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CALENDAR_ARCHIVED"));
    }

    @Test
    void returns409WhenTheSameDateIsHolidayAndSpecialDay() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-clash");
        Map<String, Object> body = new java.util.HashMap<>(calendarBody("Conflicto de fecha"));
        body.put("holidays", java.util.List.of(Map.of("date", "2026-01-06", "name", "Reyes")));
        body.put(
                "specialDays",
                java.util.List.of(Map.of("date", "2026-01-06", "name", "Tambien especial", "expectedMinutes", 240)));

        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CALENDAR_DUPLICATE_DATE"));
    }

    @Test
    void returns400ForInvalidPayloadAndParameters() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-invalid");
        String token = tenant.admin().token();

        // Nombre en blanco: Bean Validation del DTO.
        Map<String, Object> blankName = new java.util.HashMap<>(calendarBody(" "));
        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        // Zona horaria inexistente: invariante del dominio.
        Map<String, Object> badZone = new java.util.HashMap<>(calendarBody("Zona rara"));
        badZone.put("timezone", "Marte/Olympus");
        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badZone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

        // Vigencia invertida.
        Map<String, Object> badPeriod = new java.util.HashMap<>(calendarBody("Vigencia rara"));
        badPeriod.put("validFrom", "2026-06-01");
        badPeriod.put("validTo", "2026-05-01");
        mockMvc.perform(post("/api/v1/admin/calendars")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badPeriod)))
                .andExpect(status().isBadRequest());

        // Filtro de estado desconocido.
        mockMvc.perform(get("/api/v1/admin/calendars?status=NOPE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ARGUMENT"));

        // Paginacion fuera de rango.
        mockMvc.perform(get("/api/v1/admin/calendars?page=-1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/admin/calendars?size=500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForUnknownCalendar() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-missing");

        mockMvc.perform(get("/api/v1/admin/calendars/" + java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.admin().token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void writesAnOutboxMessageInTheSameTransactionForEachCalendarFact() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("cal-outbox");
        String token = tenant.admin().token();
        String calendarId = createCalendar(token, "Con eventos");

        mockMvc.perform(put("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(calendarBody("Con eventos"))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/calendars/" + calendarId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // El publicador de outbox es un punto de contribucion (ADR-0011): basta
        // con que CalendarIntegrationEventMapper este dado de alta como bean
        // para que los hechos de calendario acaben en outbox_message dentro de
        // la misma transaccion de negocio (ADR-0005).
        java.util.List<String> eventTypes = new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .queryForList(
                        "SELECT event_type FROM outbox_message WHERE tenant_id = ? AND aggregate_id = ?"
                                + " ORDER BY created_at",
                        String.class,
                        tenant.tenantId(),
                        java.util.UUID.fromString(calendarId));

        org.assertj.core.api.Assertions.assertThat(eventTypes)
                .containsExactly(
                        "calendar.calendar-created.v1",
                        "calendar.calendar-updated.v1",
                        "calendar.calendar-archived.v1");
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class AdminCalendarControllerIntegrationTestConfiguration {

        @org.springframework.context.annotation.Bean
        TestTenantFactory testTenantFactory(
                MockMvc mockMvc,
                ObjectMapper objectMapper,
                RegisterTenantUseCase registerTenantUseCase,
                com.tfp.timetracking.identity.domain.UserRepository userRepository,
                com.tfp.timetracking.identity.domain.PasswordHasher passwordHasher,
                com.tfp.timetracking.shared.domain.Clock clock,
                com.tfp.timetracking.shared.domain.IdGenerator idGenerator) {
            return new TestTenantFactory(
                    mockMvc, objectMapper, registerTenantUseCase, userRepository, passwordHasher, clock, idGenerator);
        }
    }
}
