/**
 * com.tfp.timetracking.outbox.infrastructure.demo
 *
 * <p>Consumidor de ejemplo (T704): demuestra el patron de idempotencia que
 * ADR-0005 exige a los consumidores de eventos de integracion, apoyado en la
 * tabla {@code processed_event}. No implementa ningun caso de uso de
 * negocio; existe unicamente para probar, de extremo a extremo, que la
 * redelivery (entrega at-least-once) de un mismo {@code eventId} no produce
 * efectos duplicados.
 */
package com.tfp.timetracking.outbox.infrastructure.demo;
