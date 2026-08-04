package com.tfp.timetracking.tenant.application.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationApproved;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationEmailVerified;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRejected;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRequested;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationVerificationRequested;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contratos de integración de la épica T53 (catálogo {@code docs/integration/event-catalog.md}). */
class TenantRegistrationIntegrationEventMapperTest {

    private static final TenantIntegrationEventMapper MAPPER = new TenantIntegrationEventMapper();
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-01T10:00:00Z");

    @Test
    void mapsRegistrationRequestedWithoutAnySecret() {
        UUID registrationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new TenantRegistrationRequested(
                        eventId, OCCURRED_AT, registrationId, "Acme Corp", "owner@acme.test", "PUBLIC_WEB"))
                .orElseThrow();

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("tenant.registration-requested.v1");
        assertThat(event.eventVersion()).isEqualTo(1);
        assertThat(event.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(event.aggregateId()).isEqualTo(registrationId);
        assertThat(event.aggregateType()).isEqualTo("TenantRegistration");
        assertThat(event.payload()).doesNotContainKey("verificationToken");
        assertThat(event.payload()).containsEntry("companyName", "Acme Corp");
    }

    @Test
    void mapsVerificationRequestedCarryingTheToken() {
        UUID registrationId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new TenantRegistrationVerificationRequested(
                        UUID.randomUUID(),
                        OCCURRED_AT,
                        registrationId,
                        "owner@acme.test",
                        "Jane",
                        "raw-token",
                        OCCURRED_AT.plusSeconds(3600),
                        true))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.registration-verification-requested.v1");
        assertThat(event.payload()).containsEntry("verificationToken", "raw-token");
        assertThat(event.payload()).containsEntry("resend", true);
        assertThat(event.payload()).containsEntry("email", "owner@acme.test");
    }

    @Test
    void mapsEmailVerified() {
        IntegrationEvent event = MAPPER.map(new TenantRegistrationEmailVerified(
                        UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(), "owner@acme.test"))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.registration-email-verified.v1");
        assertThat(event.payload()).containsEntry("email", "owner@acme.test");
    }

    @Test
    void mapsApprovedWithTheCreatedTenantAndOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();

        IntegrationEvent event = MAPPER.map(new TenantRegistrationApproved(
                        UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(), tenantId, ownerUserId))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.registration-approved.v1");
        assertThat(event.payload()).containsEntry("tenantId", tenantId);
        assertThat(event.payload()).containsEntry("ownerUserId", ownerUserId);
    }

    @Test
    void mapsRejectedWithTheReason() {
        IntegrationEvent event = MAPPER.map(new TenantRegistrationRejected(
                        UUID.randomUUID(), OCCURRED_AT, UUID.randomUUID(), "Dominio desechable"))
                .orElseThrow();

        assertThat(event.eventType()).isEqualTo("tenant.registration-rejected.v1");
        assertThat(event.payload()).containsEntry("reason", "Dominio desechable");
    }
}
