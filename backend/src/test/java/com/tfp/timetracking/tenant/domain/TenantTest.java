package com.tfp.timetracking.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
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
    void deactivateMarksTenantInactiveAndUpdatesTimestamp() {
        Instant registeredAt = FIXED_NOW;
        Instant deactivatedAt = FIXED_NOW.plusSeconds(60);
        Deque<Instant> instants = new ArrayDeque<>(List.of(registeredAt, deactivatedAt));
        Clock sequencedClock = instants::poll;

        Tenant tenant = Tenant.register("Acme Corp", "Europe/Madrid", sequencedClock, fixedIdGenerator(UUID.randomUUID()));
        tenant.deactivate(sequencedClock);

        assertThat(tenant.status()).isEqualTo(TenantStatus.INACTIVE);
        assertThat(tenant.isActive()).isFalse();
        assertThat(tenant.updatedAt()).isEqualTo(deactivatedAt);
        assertThat(tenant.createdAt()).isEqualTo(registeredAt);
    }

    @Test
    void reconstituteDoesNotGenerateDomainEvents() {
        Tenant tenant = Tenant.reconstitute(
                UUID.randomUUID(), "Acme Corp", TenantStatus.ACTIVE, "Europe/Madrid", FIXED_NOW, FIXED_NOW);

        assertThat(tenant.pullDomainEvents()).isEmpty();
    }

    private static IdGenerator fixedIdGenerator(UUID firstId) {
        Deque<UUID> ids = new ArrayDeque<>();
        ids.add(firstId);
        return () -> ids.isEmpty() ? UUID.randomUUID() : ids.poll();
    }
}
