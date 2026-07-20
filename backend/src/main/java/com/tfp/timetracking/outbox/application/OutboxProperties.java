package com.tfp.timetracking.outbox.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion del publicador de outbox (T703), enlazada desde
 * {@code application.yml} bajo el prefijo {@code outbox.*}.
 *
 * @param pollInterval intervalo entre lotes del publicador ({@code
 *     @Scheduled(fixedDelayString = ...)})
 * @param batchSize tamano maximo del lote reclamado en cada ejecucion
 *     (T701, {@code claimBatch})
 * @param maxAttempts numero maximo de intentos de publicacion antes de
 *     marcar el mensaje {@code FAILED} definitivamente
 * @param claimTimeout duracion del lease de un mensaje reclamado
 *     ({@code PROCESSING}); si el worker que lo reclamo muere antes de
 *     marcarlo {@code PUBLISHED}/reintento/{@code FAILED}, otro worker puede
 *     reclamarlo de nuevo pasado este tiempo (recuperacion de huerfanos)
 * @param archiveRetention antiguedad minima ({@code publishedAt}) para que
 *     un mensaje {@code PUBLISHED} sea purgado por el job diario de
 *     archivado
 * @param archiveCron expresion cron del job diario de archivado
 * @param schedulerEnabled si {@code false}, ni el publicador ni el
 *     archivador se registran como beans programados; usado en el perfil
 *     {@code test} para que los tests de otras funcionalidades no compitan
 *     con un publicador en segundo plano sobre filas de {@code
 *     outbox_message} que no les conciernen
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
        @DefaultValue("PT5S") Duration pollInterval,
        @DefaultValue("50") int batchSize,
        @DefaultValue("8") int maxAttempts,
        @DefaultValue("PT5M") Duration claimTimeout,
        @DefaultValue("P30D") Duration archiveRetention,
        @DefaultValue("0 0 3 * * *") String archiveCron,
        @DefaultValue("true") boolean schedulerEnabled) {}
