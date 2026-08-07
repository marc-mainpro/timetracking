package com.tfp.timetracking.outbox.application;

/**
 * Punto de contribucion del panel tecnico (T140-05, ADR-0011).
 *
 * <p>Cada cola con reintentos publica aqui su estado. El panel no consulta los
 * repositorios de cada modulo: eso obligaria a {@code outbox} a mirar hacia
 * {@code notification}, que ya depende de el, y cerraria un ciclo entre ambos
 * —lo detecto {@code ModuleCyclesTest} al primer intento—.
 *
 * <p>Como efecto util, una cola futura aparece en el panel implementando esta
 * interfaz, sin tocar el caso de uso ni el controlador.
 */
public interface QueueStatusContributor {

    /**
     * @param name identificador de la cola tal como se muestra en el panel
     * @param pending trabajo por procesar; se resuelve solo en la siguiente pasada
     * @param failed trabajo que agoto sus reintentos: no se recupera solo y
     *     requiere intervencion
     */
    record QueueStatus(String name, long pending, long failed) {}

    QueueStatus status();
}
