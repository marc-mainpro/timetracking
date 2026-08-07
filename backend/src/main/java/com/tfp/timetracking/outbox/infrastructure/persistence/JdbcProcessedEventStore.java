package com.tfp.timetracking.outbox.infrastructure.persistence;

import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.Clock;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Implementacion de {@link ProcessedEventStore} sobre la tabla
 * {@code processed_event}.
 *
 * <p>Usa {@code ON CONFLICT DO NOTHING} en lugar de "consultar y despues
 * insertar": la reserva queda resuelta por la clave primaria en una sola
 * sentencia, sin ventana entre la comprobacion y la marca. El numero de filas
 * afectadas <i>es</i> la respuesta —1 si la reserva es nuestra, 0 si otro la
 * tenia—, asi que no hace falta capturar la violacion de restriccion como
 * excepcion de control de flujo.
 *
 * <p>Se implementa con JDBC y no con JPA a proposito: la tabla tiene clave
 * compuesta y ningun comportamiento, mapearla como entidad con
 * {@code @IdClass} solo anadiria ceremonia.
 */
@Repository
public class JdbcProcessedEventStore implements ProcessedEventStore {

    private static final String CLAIM_SQL =
            "INSERT INTO processed_event (event_id, consumer, processed_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT (event_id, consumer) DO NOTHING";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcProcessedEventStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public boolean tryClaim(UUID eventId, String consumer) {
        return jdbcTemplate.update(CLAIM_SQL, eventId, consumer, Timestamp.from(clock.now())) == 1;
    }
}
