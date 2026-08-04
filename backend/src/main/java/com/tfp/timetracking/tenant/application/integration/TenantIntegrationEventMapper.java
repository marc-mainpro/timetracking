package com.tfp.timetracking.tenant.application.integration;

import com.tfp.timetracking.shared.application.IntegrationEventMapper;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import com.tfp.timetracking.tenant.domain.event.TenantActivated;
import com.tfp.timetracking.tenant.domain.event.TenantArchived;
import com.tfp.timetracking.tenant.domain.event.TenantReactivated;
import com.tfp.timetracking.tenant.domain.event.TenantRegistered;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationApproved;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationEmailVerified;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRejected;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationRequested;
import com.tfp.timetracking.tenant.domain.event.TenantRegistrationVerificationRequested;
import com.tfp.timetracking.tenant.domain.event.TenantSuspended;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Traduce eventos de dominio del modulo {@code tenant} a eventos de
 * integracion versionados (T702, CONTEXT-DOMINIO §4). Solo depende del
 * tipo {@link IntegrationEvent} de {@code shared.domain}, nunca de
 * infraestructura de outbox (ver {@code OutboxEncapsulationTest}).
 */
@Component
public class TenantIntegrationEventMapper implements IntegrationEventMapper {

    private static final String AGGREGATE_TYPE = "Tenant";
    private static final String REGISTRATION_AGGREGATE_TYPE = "TenantRegistration";


    /**
     * @param domainEvent evento de dominio recogido tras persistir el agregado
     * @return el evento de integracion equivalente, o {@link Optional#empty()}
     *     si el evento de dominio no tiene traduccion a integracion en este
     *     modulo
     */
    @Override
    public Optional<IntegrationEvent> map(Object domainEvent) {
        if (domainEvent instanceof TenantRegistered event) {
            return Optional.of(new IntegrationEvent(
                    event.eventId(),
                    "tenant.registered.v1",
                    1,
                    event.occurredAt(),
                    event.tenantId(),
                    event.aggregateId(),
                    AGGREGATE_TYPE,
                    Map.of(
                            "tenantId", event.aggregateId(),
                            "name", event.name(),
                            "timezone", event.timezone())));
        }
        if (domainEvent instanceof TenantActivated event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.activated.v1", event.occurredAt(), event.aggregateId(), null));
        }
        if (domainEvent instanceof TenantSuspended event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.suspended.v1", event.occurredAt(), event.aggregateId(), event.reason()));
        }
        if (domainEvent instanceof TenantReactivated event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.reactivated.v1", event.occurredAt(), event.aggregateId(), null));
        }
        if (domainEvent instanceof TenantArchived event) {
            return Optional.of(lifecycleEvent(
                    event.eventId(), "tenant.archived.v1", event.occurredAt(), event.aggregateId(), event.reason()));
        }
        return mapRegistrationEvent(domainEvent);
    }

    /**
     * Eventos del registro público (T53). Todos se emiten con el tenant de
     * plataforma como {@code tenantId}: una solicitud de alta es anterior al
     * tenant, así que no existe todavía ningún tenant al que atribuirla, y
     * quien las observa es siempre la plataforma.
     */
    private Optional<IntegrationEvent> mapRegistrationEvent(Object domainEvent) {
        if (domainEvent instanceof TenantRegistrationRequested event) {
            return Optional.of(registrationEvent(
                    event.eventId(),
                    "tenant.registration-requested.v1",
                    event.occurredAt(),
                    event.registrationId(),
                    Map.of(
                            "registrationId", event.registrationId(),
                            "companyName", event.companyName(),
                            "email", event.email(),
                            "source", event.source())));
        }
        if (domainEvent instanceof TenantRegistrationVerificationRequested event) {
            // Único evento del catálogo cuyo payload contiene un secreto: el
            // consumidor de correo lo necesita para construir el enlace. Ver
            // ADR-0016 (riesgo asumido y mitigaciones).
            Map<String, Object> payload = new HashMap<>();
            payload.put("registrationId", event.registrationId());
            payload.put("email", event.email());
            payload.put("ownerFirstName", event.ownerFirstName());
            payload.put("verificationToken", event.verificationToken());
            payload.put("expiresAt", event.expiresAt());
            payload.put("resend", event.resend());
            return Optional.of(registrationEvent(
                    event.eventId(),
                    "tenant.registration-verification-requested.v1",
                    event.occurredAt(),
                    event.registrationId(),
                    payload));
        }
        if (domainEvent instanceof TenantRegistrationEmailVerified event) {
            return Optional.of(registrationEvent(
                    event.eventId(),
                    "tenant.registration-email-verified.v1",
                    event.occurredAt(),
                    event.registrationId(),
                    Map.of("registrationId", event.registrationId(), "email", event.email())));
        }
        if (domainEvent instanceof TenantRegistrationApproved event) {
            return Optional.of(registrationEvent(
                    event.eventId(),
                    "tenant.registration-approved.v1",
                    event.occurredAt(),
                    event.registrationId(),
                    Map.of(
                            "registrationId", event.registrationId(),
                            "tenantId", event.tenantId(),
                            "ownerUserId", event.ownerUserId())));
        }
        if (domainEvent instanceof TenantRegistrationRejected event) {
            return Optional.of(registrationEvent(
                    event.eventId(),
                    "tenant.registration-rejected.v1",
                    event.occurredAt(),
                    event.registrationId(),
                    Map.of("registrationId", event.registrationId(), "reason", event.reason())));
        }
        return Optional.empty();
    }

    private static IntegrationEvent registrationEvent(
            java.util.UUID eventId,
            String eventType,
            java.time.Instant occurredAt,
            java.util.UUID registrationId,
            Map<String, Object> payload) {
        return new IntegrationEvent(
                eventId,
                eventType,
                1,
                occurredAt,
                PlatformTenant.ID,
                registrationId,
                REGISTRATION_AGGREGATE_TYPE,
                payload);
    }

    private static IntegrationEvent lifecycleEvent(
            java.util.UUID eventId, String eventType, java.time.Instant occurredAt, java.util.UUID tenantId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId);
        if (reason != null) {
            payload.put("reason", reason);
        }
        return new IntegrationEvent(eventId, eventType, 1, occurredAt, tenantId, tenantId, AGGREGATE_TYPE, payload);
    }
}
