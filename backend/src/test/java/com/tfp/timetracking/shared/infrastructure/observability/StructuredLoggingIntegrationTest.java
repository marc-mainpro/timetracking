package com.tfp.timetracking.shared.infrastructure.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.infrastructure.security.CorrelationIdFilter;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T140-01 y T140-02 de punta a punta: una peticion real produce lineas de log
 * JSON con los campos de RO-002, y ninguna de esas lineas contiene la credencial
 * que viajaba en la peticion (RS-014).
 *
 * <p>El formato se ejercita enchufando el propio {@link StructuredLogEncoder} de
 * Spring Boot —el mismo que usa {@code logging.structured.format.console=ecs}—
 * a un appender de memoria, en vez de capturar la consola. La razon es que Boot
 * inicializa el sistema de logging <b>una sola vez por JVM</b>: en una suite
 * completa gana la configuracion del primer contexto que arranca, asi que
 * afirmar sobre la consola haria que este test pasara o fallara segun el orden
 * de ejecucion. Con el appender propio, lo que se comprueba es exactamente lo
 * que produciria produccion, sin depender de quien arranco antes.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.com.tfp.timetracking=DEBUG")
class StructuredLoggingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingIntegrationTest.class);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("timetracking")
            .withUsername("timetracking")
            .withPassword("timetracking");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    @Autowired
    private TestTenantFactory testTenantFactory;

    private final ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
    private OutputStreamAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachEcsAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject(Environment.class.getName(), environment);

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(loggerContext);
        encoder.setFormat("ecs");
        encoder.setCharset(StandardCharsets.UTF_8);
        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setContext(loggerContext);
        appender.setEncoder(encoder);
        appender.setOutputStream(logOutput);
        appender.start();
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @AfterEach
    void detachEcsAppender() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        appender.stop();
    }

    @Test
    void everyRequestProducesAJsonLineWithTheFieldsOfRo002() throws Exception {
        TestTenantFactory.TenantActors actors = testTenantFactory.createTenantActors("logs");
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actors.admin().token())
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                                .isEqualTo(correlationId));

        JsonNode line = jsonLinesWith(correlationId).stream()
                .filter(node -> node.hasNonNull("useCase"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ninguna linea de log llevaba correlationId y useCase"));

        // timestamp y level los aporta el formateador ECS (anidados); las claves
        // del MDC salen planas en el primer nivel del JSON, que es justo por lo
        // que se eligio ECS: `correlationId` es consultable sin parsear el
        // mensaje.
        assertThat(line.path("@timestamp").asText()).isNotBlank();
        assertThat(line.path("log").path("level").asText()).isNotBlank();
        assertThat(line.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(line.path("tenantId").asText()).isEqualTo(actors.tenantId().toString());
        assertThat(line.path("userId").asText()).isEqualTo(actors.admin().userId().toString());
        assertThat(line.path("useCase").asText()).isEqualTo("EmployeeController#list");
    }

    @Test
    void marksTheResultOfTheRequest() throws Exception {
        TestTenantFactory.TenantActors actors = testTenantFactory.createTenantActors("logs-result");
        String correlationId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actors.admin().token())
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId))
                .andExpect(status().isOk());

        assertThat(jsonLinesWith(correlationId))
                .anySatisfy(line -> assertThat(line.path("result").asText()).isEqualTo("SUCCESS"));
    }

    /** Sin cabecera entrante se genera uno: ninguna peticion queda sin correlacion (RNF-020). */
    @Test
    void generatesACorrelationIdWhenTheClientDoesNotSendOne() throws Exception {
        TestTenantFactory.TenantActors actors = testTenantFactory.createTenantActors("logs-generated");

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actors.admin().token()))
                .andExpect(status().isOk())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                                .isNotBlank());
    }

    /**
     * RS-014. La peticion lleva un access token en la cabecera y el alta previa
     * llevo una contrasena en el cuerpo; el log no puede llevar ninguno de los
     * dos, ni entero ni por fragmentos.
     */
    @Test
    void neverWritesTheBearerTokenNorPasswordsToTheLog() throws Exception {
        TestTenantFactory.TenantActors actors = testTenantFactory.createTenantActors("logs-secretos");

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actors.admin().token())
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isOk());

        String logged = logOutput.toString(StandardCharsets.UTF_8);
        assertThat(logged).isNotEmpty();
        assertThat(logged).doesNotContain(actors.admin().token());
        assertThat(logged).doesNotContain(actors.admin().password());
        assertThat(logged).doesNotContain("refresh_token=");
    }

    /** El MDC no puede sobrevivir a la peticion: el hilo del contenedor se reutiliza. */
    @Test
    void doesNotLeakTheContextToLaterLinesOnTheSameThread() throws Exception {
        TestTenantFactory.TenantActors actors = testTenantFactory.createTenantActors("logs-fuga");

        mockMvc.perform(get("/api/v1/employees")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + actors.admin().token())
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        log.info("linea-posterior-a-la-peticion");

        assertThat(jsonLines())
                .filteredOn(line -> line.path("message").asText().equals("linea-posterior-a-la-peticion"))
                .isNotEmpty()
                .allSatisfy(line -> {
                    assertThat(line.has("correlationId")).isFalse();
                    assertThat(line.has("tenantId")).isFalse();
                });
    }

    private List<JsonNode> jsonLinesWith(String correlationId) {
        return jsonLines().stream()
                .filter(node -> correlationId.equals(node.path("correlationId").asText()))
                .toList();
    }

    private List<JsonNode> jsonLines() {
        return logOutput.toString(StandardCharsets.UTF_8)
                .lines()
                .map(String::trim)
                .filter(line -> line.startsWith("{") && line.endsWith("}"))
                .map(this::parseOrNull)
                .filter(node -> node != null)
                .toList();
    }

    private JsonNode parseOrNull(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (Exception ignored) {
            return null;
        }
    }

    @TestConfiguration
    static class StructuredLoggingTestConfiguration {

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
