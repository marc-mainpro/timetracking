package com.tfp.timetracking.outbox.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo del descarte.
 *
 * <p>Es obligatorio: descartar renuncia a un trabajo que fallo y la unica traza
 * de por que se hizo es lo que escriba quien lo decide. Sin motivo, la
 * auditoria diria quien lo descarto pero no por que, que es justo lo que hace
 * falta meses despues.
 */
public record DiscardQueueEntryRequest(@NotBlank @Size(max = 500) String reason) {}
