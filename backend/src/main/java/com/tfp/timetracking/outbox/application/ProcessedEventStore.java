package com.tfp.timetracking.outbox.application;

import java.util.UUID;

/**
 * Registro de deduplicacion que hace idempotentes a los consumidores internos
 * de eventos de integracion (RF-OUT-005, ADR-0005).
 *
 * <p>La entrega es at-least-once: un fallo en cualquier consumidor hace que el
 * mensaje se reintente y que <b>todos</b> los consumidores vuelvan a recibirlo,
 * incluidos los que ya habian terminado bien. Sin este registro, ese reintento
 * duplicaria efectos visibles para el usuario, como enviar dos veces el correo
 * de recuperacion de contrasena.
 *
 * <p>La clave es {@code (eventId, consumer)} y no solo {@code eventId}: cada
 * consumidor lleva su propia cuenta. Con una clave por evento, el primero en
 * marcarlo dejaria a los demas creyendo que ya estaba procesado.
 */
public interface ProcessedEventStore {

    /**
     * Reserva el evento para este consumidor.
     *
     * <p>Es una operacion atomica de comprobar-y-marcar: si dos hilos compiten
     * por el mismo par, solo uno recibe {@code true}. Por eso devuelve el
     * resultado en lugar de exponer un {@code exists} separado, que dejaria una
     * ventana de carrera entre la comprobacion y la marca.
     *
     * @return {@code true} si es la primera vez que este consumidor ve el
     *     evento y debe procesarlo; {@code false} si ya lo proceso antes
     */
    boolean tryClaim(UUID eventId, String consumer);
}
