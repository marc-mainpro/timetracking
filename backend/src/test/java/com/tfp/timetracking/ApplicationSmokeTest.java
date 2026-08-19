package com.tfp.timetracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.outbox.application.FailedQueueMaintenance;
import com.tfp.timetracking.outbox.application.QueueStatusContributor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Test de humo (T101): el contexto de Spring arranca correctamente contra un
 * PostgreSQL real levantado con Testcontainers, con Flyway aplicando el
 * baseline vacio.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("timetracking")
                    .withUsername("timetracking")
                    .withPassword("timetracking");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void contextLoads(ApplicationContext context) {
        assertThat(context).isNotNull();
        assertThat(context.containsBean("securityFilterChain")).isTrue();
    }

    /**
     * Cada cola que el panel muestra debe poder intervenirse.
     *
     * <p>La correspondencia entre {@code QueueStatusContributor} y {@code
     * FailedQueueMaintenance} es por nombre de cola, es decir por convencion de
     * cadenas: si alguien añade una cola nueva y solo implementa la primera, el
     * panel mostrara fallos sin ofrecer forma de resolverlos y nadie se
     * enterara. Se comprueba aqui, sobre el contexto que este test ya levanta,
     * para no pagar un arranque de Spring adicional.
     */
    @Test
    void everyReportedQueueCanBeMaintained(ApplicationContext context) {
        assertThat(context.getBeansOfType(QueueStatusContributor.class).values().stream()
                        .map(contributor -> contributor.status().name()))
                .isNotEmpty()
                .allSatisfy(queue -> assertThat(context.getBeansOfType(FailedQueueMaintenance.class).values())
                        .as("la cola '%s' aparece en el panel pero no admite intervencion manual", queue)
                        .anySatisfy(maintenance ->
                                assertThat(maintenance.queueName()).isEqualTo(queue)));
    }
}
