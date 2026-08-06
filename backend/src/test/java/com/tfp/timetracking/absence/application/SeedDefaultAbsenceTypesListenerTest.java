package com.tfp.timetracking.absence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.absence.domain.AbsenceType;
import com.tfp.timetracking.absence.domain.AbsenceTypeRepository;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SeedDefaultAbsenceTypesListenerTest {

    private final AbsenceTypeRepository repository = mock(AbsenceTypeRepository.class);
    private final ProcessedEventStore processedEventStore = mock(ProcessedEventStore.class);
    private final SeedDefaultAbsenceTypesListener listener =
            new SeedDefaultAbsenceTypesListener(repository, processedEventStore, UUID::randomUUID);

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void seedsTheCatalogueRequiredByTheRequirementWhenATenantIsCreated() {
        // Sin catálogo, un empleado no puede solicitar ninguna ausencia: era el
        // estado en el que nacía todo tenant (RF-ABS-001).
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());

        listener.onEvent(event("tenant.registered.v1"));

        ArgumentCaptor<AbsenceType> captor = ArgumentCaptor.forClass(AbsenceType.class);
        verify(repository, times(5)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AbsenceType::code)
                .containsExactlyInAnyOrder("VACACIONES", "PERMISO", "BAJA", "JUSTIFICADA", "NO_JUSTIFICADA");
        assertThat(captor.getAllValues()).allSatisfy(type -> assertThat(type.tenantId()).isEqualTo(tenantId));
    }

    @Test
    void alsoSeedsWhenTheTenantComesFromAnApprovedRegistration() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());

        listener.onEvent(event("tenant.registration-approved.v1"));

        verify(repository, times(5)).save(any());
    }

    @Test
    void ignoresEventsThatDoNotCreateATenant() {
        listener.onEvent(event("tenant.suspended.v1"));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void doesNotDuplicateTheCatalogueOnRedelivery() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(false);

        listener.onEvent(event("tenant.registered.v1"));

        verify(repository, never()).save(any());
    }

    @Test
    void doesNotSeedTwiceIfTheTenantAlreadyHasTypes() {
        // Un mismo tenant puede recibir más de un evento de alta según cómo se
        // haya creado, así que la reserva no basta como única defensa.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(repository.findByTenantId(tenantId))
                .thenReturn(List.of(AbsenceType.create(tenantId, "VACACIONES", "Vacaciones", true, false, UUID.randomUUID())));

        listener.onEvent(event("tenant.registered.v1"));

        verify(repository, never()).save(any());
    }

    private IntegrationEvent event(String eventType) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.parse("2026-08-06T10:00:00Z"),
                tenantId,
                tenantId,
                "Tenant",
                Map.of("tenantId", tenantId));
    }
}
