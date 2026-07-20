package com.tfp.timetracking.shared.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * {@link ObjectMapper} dedicado de la aplicacion (T702, CONTEXT-DOMINIO §4).
 *
 * <p>Su consumidor principal es la serializacion del {@code payload} de los
 * eventos de integracion antes de escribirlos en la columna {@code jsonb} de
 * {@code outbox_message} ({@code outbox.infrastructure.persistence.OutboxMessageMapper}):
 * las fechas deben quedar en ISO-8601 UTC, nunca como timestamp numerico, para
 * que el contrato publicado en {@code docs/integration/event-catalog.md} sea
 * estable para consumidores externos.
 *
 * <p>Se declara explicitamente (en vez de depender del {@code ObjectMapper}
 * autoconfigurado implicitamente por Spring Boot) para que esta politica de
 * fechas quede documentada y bajo control de este modulo; al ser el unico
 * bean de tipo {@link ObjectMapper} del contexto, tambien es el que usa Spring
 * MVC para (de)serializar los DTOs de la API REST. Por eso {@code Duration}
 * se serializa explicitamente como ISO-8601 ({@code WRITE_DURATIONS_AS_TIMESTAMPS}
 * deshabilitado) y no como numero de segundos, igual que hacia el
 * autoconfigurado por defecto de Spring Boot (contrato ya usado por
 * {@code reporting.interfaces.rest}).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                        SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .modulesToInstall(new JavaTimeModule())
                .build();
    }
}
