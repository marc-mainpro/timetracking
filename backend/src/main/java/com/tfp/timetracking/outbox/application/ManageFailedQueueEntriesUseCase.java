package com.tfp.timetracking.outbox.application;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.outbox.application.FailedQueueMaintenance.FailedQueueEntry;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantenimiento manual de las colas fallidas desde el panel de plataforma:
 * listar, reintentar y descartar.
 *
 * <p>Es generico sobre la cola. Resuelve el {@link FailedQueueMaintenance} por
 * nombre y delega; asi una cola futura gana estas tres operaciones solo
 * implementando el puerto, sin tocar este caso de uso ni el controlador.
 *
 * <p>La auditoria se registra <b>aqui y no en cada adaptador</b>: es la misma
 * accion de plataforma independientemente de la cola, y centralizarla evita que
 * cada modulo tenga que depender de {@code audit} para decir lo mismo.
 */
@Service
public class ManageFailedQueueEntriesUseCase {

    private final Map<String, FailedQueueMaintenance> queues;
    private final AuditRecorder auditRecorder;

    public ManageFailedQueueEntriesUseCase(List<FailedQueueMaintenance> queues, AuditRecorder auditRecorder) {
        Map<String, FailedQueueMaintenance> byName = new LinkedHashMap<>();
        queues.forEach(queue -> byName.put(queue.queueName(), queue));
        this.queues = Map.copyOf(byName);
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public PagedResult<FailedQueueEntry> listFailed(String queue, int page, int size) {
        return queueNamed(queue).listFailed(page, size);
    }

    @Transactional
    public void retry(String queue, UUID entryId) {
        FailedQueueEntry entry = queueNamed(queue).retry(entryId);
        audit("PLATFORM_QUEUE_ENTRY_RETRIED", queue, entry, null);
    }

    @Transactional
    public void discard(String queue, UUID entryId, String reason) {
        FailedQueueEntry entry = queueNamed(queue).discard(entryId);
        audit("PLATFORM_QUEUE_ENTRY_DISCARDED", queue, entry, reason.trim());
    }

    /**
     * Una cola inexistente es un recurso que no existe, no un fallo del
     * servidor: el nombre llega en la URL y puede venir de un enlace viejo.
     */
    private FailedQueueMaintenance queueNamed(String queue) {
        FailedQueueMaintenance maintenance = queues.get(queue);
        if (maintenance == null) {
            throw new ResourceNotFoundException("Cola no encontrada: " + queue);
        }
        return maintenance;
    }

    /**
     * El evento se atribuye al tenant de plataforma, como el resto de acciones
     * de {@code PLATFORM_ADMIN}; el tenant <b>afectado</b> viaja en metadata,
     * que es lo que permite reconstruir a quien impacto la decision.
     */
    private void audit(String action, String queue, FailedQueueEntry entry, String reason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("queue", queue);
        metadata.put("type", entry.type());
        metadata.put("attempts", entry.attempts());
        if (entry.tenantId() != null) {
            metadata.put("affectedTenantId", entry.tenantId().toString());
        }
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditRecorder.record(action, "QueueEntry", entry.id(), metadata);
    }
}
