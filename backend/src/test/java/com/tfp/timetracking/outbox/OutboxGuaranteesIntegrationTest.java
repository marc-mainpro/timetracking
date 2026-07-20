package com.tfp.timetracking.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.identity.domain.PasswordHasher;
import com.tfp.timetracking.identity.domain.UserRepository;
import com.tfp.timetracking.outbox.application.PublishPendingOutboxMessages;
import com.tfp.timetracking.outbox.domain.OutboxMessage;
import com.tfp.timetracking.outbox.domain.OutboxMessageRepository;
import com.tfp.timetracking.outbox.domain.OutboxMessageStatus;
import com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer;
import com.tfp.timetracking.outbox.infrastructure.demo.DemoIdempotentEventConsumer.ConsumptionResult;
import com.tfp.timetracking.outbox.infrastructure.demo.ProcessedEventJpaRepository;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shared.infrastructure.security.TestTenantFactory;
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

/**
 * Prueba de extremo a extremo (T704, Testcontainers PostgreSQL) del flujo
 * completo negocio -&gt; outbox -&gt; publicador -&gt; consumidor demostrado por la
 * iteracion 7: cerrar una jornada real (accion de negocio) deja un mensaje
 * {@code PENDING} en {@code outbox_message} en la misma transaccion
 * (atomicidad, re-verificada aqui igual que en
 * {@code EndWorkdayUseCaseAtomicityIntegrationTest} de T702); el publicador
 * ({@link PublishPendingOutboxMessages}) lo entrega y lo marca {@code
 * PUBLISHED}; el consumidor de demostracion idempotente ({@link
 * DemoIdempotentEventConsumer}), enganchado como {@code
 * IntegrationEventListener} de {@code LoggingIntegrationEventPublisher}, lo
 * procesa exactamente una vez.
 *
 * <p>El criterio de aceptacion central de la ficha ("los reintentos no
 * producen efectos duplicados") se demuestra invocando el consumidor una
 * <strong>segunda vez</strong> con el mismo evento reconstruido desde
 * {@code outbox_message} (simulando la redelivery que un broker real
 * produciria bajo entrega at-least-once, ver ADR-0005): la fila de {@code
 * processed_event} sigue siendo unica y el contador de efectos aplicados no
 * crece.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OutboxGuaranteesIntegrationTest {

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

    @Autowired
    private PublishPendingOutboxMessages publishPendingOutboxMessages;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private DemoIdempotentEventConsumer demoIdempotentEventConsumer;

    @Autowired
    private ProcessedEventJpaRepository processedEventRepository;

    @Test
    void businessActionFlowsThroughOutboxAndPublisherToAnIdempotentConsumerWithoutDuplicateEffectsOnRedelivery()
            throws Exception {
        TestTenantFactory.TenantActors tenant = testTenantFactory.createTenantActors("outbox-guarantees");

        // 1) Accion de negocio real: abrir y cerrar una jornada.
        String startResponse = mockMvc.perform(post("/api/v1/workdays/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String workdayId = objectMapper.readTree(startResponse).get("id").asText();

        mockMvc.perform(post("/api/v1/workdays/current/end")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenant.employee().token()))
                .andExpect(status().isOk());

        // 2) Atomicidad negocio+outbox (re-verificacion de flujo completo, T702/T704):
        // el evento queda PENDING en la misma transaccion que el cierre.
        UUID closedEventId = UUID.fromString(jdbcTemplate.queryForObject(
                "select id::text from outbox_message where aggregate_id = ?::uuid and event_type = ?",
                String.class,
                workdayId,
                "time-tracking.workday-closed.v1"));
        OutboxMessage pending = outboxMessageRepository.findById(closedEventId).orElseThrow();
        assertThat(pending.status()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(processedEventRepository.existsById(closedEventId))
                .as("no se procesa nada antes de que el publicador entregue el mensaje")
                .isFalse();

        int effectsBeforePublish = demoIdempotentEventConsumer.effectsAppliedCount();

        // 3) Publicador: reclama y entrega el lote (incluye workday-started y workday-closed).
        int processed = publishPendingOutboxMessages.publishBatch();
        assertThat(processed).isGreaterThanOrEqualTo(1);

        OutboxMessage published = outboxMessageRepository.findById(closedEventId).orElseThrow();
        assertThat(published.status()).isEqualTo(OutboxMessageStatus.PUBLISHED);
        assertThat(published.publishedAt()).isNotNull();

        // 4) El consumidor demo, enganchado al publicador (T704), ya proceso el evento una vez.
        assertThat(processedEventRepository.existsById(closedEventId)).isTrue();
        int effectsAfterAutomaticDelivery = demoIdempotentEventConsumer.effectsAppliedCount();
        assertThat(effectsAfterAutomaticDelivery).isGreaterThan(effectsBeforePublish);

        // 5) Redelivery deliberada: reconstruimos el mismo IntegrationEvent (mismo eventId,
        // igual que haria un broker real reentregando el mensaje) y lo consumimos dos veces mas.
        IntegrationEvent redelivered = new IntegrationEvent(
                published.id(),
                published.eventType(),
                published.eventVersion(),
                published.occurredAt(),
                published.tenantId(),
                published.aggregateId(),
                published.aggregateType(),
                published.payload());

        ConsumptionResult firstRedelivery = demoIdempotentEventConsumer.consume(redelivered);
        ConsumptionResult secondRedelivery = demoIdempotentEventConsumer.consume(redelivered);

        assertThat(firstRedelivery).isEqualTo(ConsumptionResult.DUPLICATE_IGNORED);
        assertThat(secondRedelivery).isEqualTo(ConsumptionResult.DUPLICATE_IGNORED);

        // Ningun efecto adicional se aplico, y sigue habiendo una unica fila de deduplicacion.
        assertThat(demoIdempotentEventConsumer.effectsAppliedCount()).isEqualTo(effectsAfterAutomaticDelivery);
        Long processedRowsForEvent = jdbcTemplate.queryForObject(
                "select count(*) from processed_event where event_id = ?::uuid",
                Long.class,
                closedEventId.toString());
        assertThat(processedRowsForEvent).isEqualTo(1L);
    }

    @TestConfiguration
    static class OutboxGuaranteesTestConfiguration {

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
