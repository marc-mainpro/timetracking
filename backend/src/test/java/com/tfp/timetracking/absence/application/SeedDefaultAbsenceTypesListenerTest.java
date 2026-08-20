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
import com.tfp.timetracking.shared.domain.PlatformTenant;
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
    void seedsTheTenantOfThePayloadWhenAPublicRegistrationIsApproved() {
        // El evento del registro público viaja con el tenant de plataforma en el
        // envelope: la solicitud es anterior al tenant. Tomar ese id sembraba el
        // catálogo en la plataforma y dejaba sin tipos a todo tenant nacido por
        // alta pública.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());

        listener.onEvent(approvedRegistrationEvent(tenantId));

        ArgumentCaptor<AbsenceType> captor = ArgumentCaptor.forClass(AbsenceType.class);
        verify(repository, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(type -> assertThat(type.tenantId()).isEqualTo(tenantId));
    }

    @Test
    void readsTheTenantOfThePayloadEvenAfterTheOutboxSerialisesIt() {
        // Al pasar por el outbox el payload se serializa y el UUID vuelve como
        // cadena.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(repository.findByTenantId(tenantId)).thenReturn(List.of());

        listener.onEvent(approvedRegistrationEvent(tenantId.toString()));

        ArgumentCaptor<AbsenceType> captor = ArgumentCaptor.forClass(AbsenceType.class);
        verify(repository, times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(type -> assertThat(type.tenantId()).isEqualTo(tenantId));
    }

    @Test
    void neverSeedsThePlatformTenant() {
        // No es un tenant de negocio: nadie solicita ausencias en él.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);

        listener.onEvent(approvedRegistrationEvent(PlatformTenant.ID));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
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

    /**
     * Reproduce el envelope que emite de verdad
     * {@code TenantIntegrationEventMapper} para el alta pública: tenant de
     * plataforma en el envelope y tenant real solo en el payload.
     */
    private IntegrationEvent approvedRegistrationEvent(Object payloadTenantId) {
        UUID registrationId = UUID.randomUUID();
        return new IntegrationEvent(
                UUID.randomUUID(),
                "tenant.registration-approved.v1",
                1,
                Instant.parse("2026-08-06T10:00:00Z"),
                PlatformTenant.ID,
                registrationId,
                "TenantRegistration",
                Map.of(
                        "registrationId", registrationId,
                        "tenantId", payloadTenantId,
                        "ownerUserId", UUID.randomUUID()));
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
