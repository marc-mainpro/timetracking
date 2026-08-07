package com.tfp.timetracking.absence.application;

import com.tfp.timetracking.absence.domain.AbsenceType;
import com.tfp.timetracking.absence.domain.AbsenceTypeRepository;
import com.tfp.timetracking.outbox.application.IntegrationEventListener;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Da de alta el catalogo de tipos de ausencia de un tenant recien creado
 * (RF-ABS-001).
 *
 * <p>Sin esto la funcionalidad de ausencias es inalcanzable: los tipos son
 * tenant-scoped, ninguna migracion los sembraba y no hay endpoint que los cree,
 * de modo que todo tenant nacia con el catalogo vacio y sus empleados no podian
 * solicitar nada. Lo destaparon las pruebas E2E (T160-01).
 *
 * <p>Se hace reaccionando al evento de alta y no dentro del caso de uso que
 * crea el tenant para no acoplar {@code tenant} con {@code absence}: la
 * dependencia va de quien reacciona hacia el bus, como el resto de consumidores.
 *
 * <p>Es idempotente por partida doble: reserva el par {@code (eventId,
 * consumidor)} y ademas comprueba si el tenant ya tiene tipos, porque el mismo
 * tenant recibe varios eventos de alta segun como se haya creado (registro
 * aprobado o alta directa desde plataforma).
 */
@Component
public class SeedDefaultAbsenceTypesListener implements IntegrationEventListener {

    static final String CONSUMER = "absence-default-types";

    /** Catalogo minimo exigido por RF-ABS-001. */
    private static final List<DefaultType> DEFAULT_TYPES = List.of(
            new DefaultType("VACACIONES", "Vacaciones", true),
            new DefaultType("PERMISO", "Permiso", true),
            new DefaultType("BAJA", "Baja médica", true),
            new DefaultType("JUSTIFICADA", "Ausencia justificada", true),
            new DefaultType("NO_JUSTIFICADA", "Ausencia no justificada", false));

    private static final List<String> TENANT_CREATED_EVENTS =
            List.of("tenant.registered.v1", "tenant.registration-approved.v1");

    private final AbsenceTypeRepository absenceTypeRepository;
    private final ProcessedEventStore processedEventStore;
    private final IdGenerator idGenerator;

    public SeedDefaultAbsenceTypesListener(
            AbsenceTypeRepository absenceTypeRepository,
            ProcessedEventStore processedEventStore,
            IdGenerator idGenerator) {
        this.absenceTypeRepository = absenceTypeRepository;
        this.processedEventStore = processedEventStore;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void onEvent(IntegrationEvent event) {
        if (!TENANT_CREATED_EVENTS.contains(event.eventType()) || event.tenantId() == null) {
            return;
        }
        if (!processedEventStore.tryClaim(event.eventId(), CONSUMER)) {
            return;
        }
        seedFor(event.tenantId());
    }

    private void seedFor(UUID tenantId) {
        if (!absenceTypeRepository.findByTenantId(tenantId).isEmpty()) {
            return;
        }
        for (DefaultType type : DEFAULT_TYPES) {
            absenceTypeRepository.save(AbsenceType.create(
                    tenantId, type.code(), type.name(), type.requiresApproval(), false, idGenerator.newId()));
        }
    }

    private record DefaultType(String code, String name, boolean requiresApproval) {}
}
