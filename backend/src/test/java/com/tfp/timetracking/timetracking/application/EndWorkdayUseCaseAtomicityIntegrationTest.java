package com.tfp.timetracking.timetracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import java.util.List;
import java.util.Map;
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

/**
 * Prueba de integracion (Testcontainers PostgreSQL) de la ficha T702: al
 * cerrar una jornada de trabajo con exito (commit real), debe quedar
 * exactamente una fila {@code PENDING} en {@code outbox_message} con el
 * evento {@code time-tracking.workday-closed.v1} y el payload minimo
 * esperado, escrita en la misma transaccion que el cierre de la jornada.
 *
 * <p>Este es el caso de referencia explicito de la ficha T702.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EndWorkdayUseCaseAtomicityIntegrationTest {

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
    void closingWorkdayWritesPendingOutboxMessageWithReferencePayloadInSameTransaction() throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("end-workday-outbox");

        String startResponse = mockMvc.perform(post("/api/v1/workdays/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String workdayId = objectMapper.readTree(startResponse).get("id").asText();

        String endResponse = mockMvc.perform(post("/api/v1/workdays/current/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode closed = objectMapper.readTree(endResponse);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "select status, event_type, event_version, payload::text as payload_text "
                        + "from outbox_message where aggregate_id = ?::uuid and event_type = ?",
                workdayId,
                "time-tracking.workday-closed.v1");

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("event_version")).isEqualTo(1);

        JsonNode payload = objectMapper.readTree((String) row.get("payload_text"));
        assertThat(payload.get("workdayId").asText()).isEqualTo(workdayId);
        assertThat(payload.get("employeeId").asText())
                .isEqualTo(tenant.employee().userId().toString());
        assertThat(payload.get("startedAt").asText())
                .isEqualTo(closed.get("startedAt").asText());
        assertThat(payload.get("endedAt").asText()).isEqualTo(closed.get("endedAt").asText());
    }

    @TestConfiguration
    static class EndWorkdayAtomicityTestConfiguration {

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
