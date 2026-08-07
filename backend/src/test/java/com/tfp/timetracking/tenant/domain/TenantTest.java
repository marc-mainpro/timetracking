package com.tfp.timetracking.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantActivated;
import com.tfp.timetracking.tenant.domain.event.TenantArchived;
import com.tfp.timetracking.tenant.domain.event.TenantReactivated;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.tenant.domain.event.TenantSuspended;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del agregado Tenant (CONTEXT-DOMINIO §1, ficha T202):
 * validaciones de factoria, transicion deactivate() y evento TenantRegistered.
 */
class TenantTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-15T10:00:00Z");

    private final Clock fixedClock = () -> FIXED_NOW;

    @Test
    void registersActiveTenantWithGeneratedIdAndTimestamps() {
        IdGenerator idGenerator = fixedIdGenerator(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, idGenerator);

        assertThat(tenant.id()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertThat(tenant.name()).isEqualTo("Acme Corp");
        assertThat(tenant.timezone()).isEqualTo("Europe/Madrid");
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.isActive()).isTrue();
        assertThat(tenant.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(tenant.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void trimsTenantName() {
        Tenant tenant = Tenant.register("  Acme Corp  ", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));

        assertThat(tenant.name()).isEqualTo("Acme Corp");
    }

    @Test
    void registerGeneratesTenantRegisteredEvent() {
        UUID tenantId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(tenantId));

        List<Object> events = tenant.pullDomainEvents();

        assertThat(events).hasSize(1);
        TenantRegistered event = (TenantRegistered) events.get(0);
        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.aggregateId()).isEqualTo(tenantId);
        assertThat(event.name()).isEqualTo("Acme Corp");
        assertThat(event.timezone()).isEqualTo("Europe/Madrid");
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void pullDomainEventsClearsAccumulatedEvents() {
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));

        tenant.pullDomainEvents();
        List<Object> secondPull = tenant.pullDomainEvents();

        assertThat(secondPull).isEmpty();
    }

    @Test
    void rejectsBlankName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tenant.register("   ", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsNullName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tenant.register(null, "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsInvalidTimezone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tenant.register("Acme Corp", "Not/AZone", fixedClock, fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void rejectsBlankTimezone() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tenant.register("Acme Corp", "  ", fixedClock, fixedIdGenerator(UUID.randomUUID())));
    }

    @Test
    void requestRegistrationCreatesPendingTenantWithoutEvents() {
        Tenant tenant =
                Tenant.requestRegistration("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));

        assertThat(tenant.status()).isEqualTo(TenantStatus.PENDING);
        assertThat(tenant.isActive()).isFalse();
        assertThat(tenant.activatedAt()).isNull();
        assertThat(tenant.pullDomainEvents()).isEmpty();
    }

    @Test
    void activateMovesPendingToActiveAndEmitsEvent() {
        Instant activatedAt = FIXED_NOW.plusSeconds(60);
        Clock sequencedClock = new ArrayDeque<>(List.of(FIXED_NOW, activatedAt))::poll;
        Tenant tenant =
                Tenant.requestRegistration("Acme Corp", "Europe/Madrid", sequencedClock, fixedIdGenerator(UUID.randomUUID()));

        tenant.activate(sequencedClock, UUID::randomUUID);

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.isActive()).isTrue();
        assertThat(tenant.activatedAt()).isEqualTo(activatedAt);
        assertThat(tenant.pullDomainEvents()).hasSize(1).first().isInstanceOf(TenantActivated.class);
    }

    @Test
    void suspendRequiresReasonAndMovesActiveToSuspended() {
        Instant suspendedAt = FIXED_NOW.plusSeconds(60);
        Clock sequencedClock = new ArrayDeque<>(List.of(FIXED_NOW, suspendedAt))::poll;
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", sequencedClock, fixedIdGenerator(UUID.randomUUID()));
        tenant.pullDomainEvents();

        tenant.suspend("Impago reiterado", sequencedClock, UUID::randomUUID);

        assertThat(tenant.status()).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(tenant.suspensionReason()).isEqualTo("Impago reiterado");
        assertThat(tenant.suspendedAt()).isEqualTo(suspendedAt);
        assertThat(tenant.pullDomainEvents()).hasSize(1).first().isInstanceOf(TenantSuspended.class);
    }

    @Test
    void suspendRejectsBlankReason() {
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));
        assertThatIllegalArgumentException().isThrownBy(() -> tenant.suspend("  ", fixedClock, UUID::randomUUID));
    }

    @Test
    void reactivateMovesSuspendedToActiveAndClearsReason() {
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));
        tenant.suspend("Impago", fixedClock, UUID::randomUUID);
        tenant.pullDomainEvents();

        tenant.reactivate(fixedClock, UUID::randomUUID);

        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(tenant.suspensionReason()).isNull();
        assertThat(tenant.pullDomainEvents()).hasSize(1).first().isInstanceOf(TenantReactivated.class);
    }

    @Test
    void archiveMovesActiveToArchivedAndIsTerminal() {
        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));
        tenant.pullDomainEvents();

        tenant.archive(null, fixedClock, UUID::randomUUID);

        assertThat(tenant.status()).isEqualTo(TenantStatus.ARCHIVED);
        assertThat(tenant.archivedAt()).isEqualTo(FIXED_NOW);
        assertThat(tenant.pullDomainEvents()).hasSize(1).first().isInstanceOf(TenantArchived.class);
    }

    @Test
    void rejectsInvalidTransitions() {
        Tenant pending =
                Tenant.requestRegistration("Acme", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));
        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> pending.suspend("x", fixedClock, UUID::randomUUID));

        Tenant active = Tenant.register("Acme", "Europe/Madrid", fixedClock, fixedIdGenerator(UUID.randomUUID()));
        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> active.activate(fixedClock, UUID::randomUUID));
        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> active.reactivate(fixedClock, UUID::randomUUID));

        active.archive("cierre", fixedClock, UUID::randomUUID);
        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> active.suspend("x", fixedClock, UUID::randomUUID));
        assertThatExceptionOfType(IllegalTenantTransitionException.class)
                .isThrownBy(() -> active.archive("x", fixedClock, UUID::randomUUID));
    }

    @Test
    void reconstituteDoesNotGenerateDomainEvents() {
        Tenant tenant = Tenant.reconstitute(
                UUID.randomUUID(),
                "Acme Corp",
                TenantStatus.ACTIVE,
                "Europe/Madrid",
                FIXED_NOW,
                FIXED_NOW,
                FIXED_NOW,
                null,
                null,
                null);

        assertThat(tenant.pullDomainEvents()).isEmpty();
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
