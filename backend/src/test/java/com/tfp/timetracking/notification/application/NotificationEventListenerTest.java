package com.tfp.timetracking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.notification.domain.NotificationType;
import com.tfp.timetracking.outbox.application.ProcessedEventStore;
import com.tfp.timetracking.shared.domain.IntegrationEvent;
import com.tfp.timetracking.shared.domain.PlatformTenant;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationEventListenerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final UserDirectoryQuery directory = mock(UserDirectoryQuery.class);
    private final RoleRecipientQuery roleQuery = mock(RoleRecipientQuery.class);
    private final ProcessedEventStore processedEventStore = mock(ProcessedEventStore.class);
    private final NotificationEventListener listener = new NotificationEventListener(
            repository,
            directory,
            roleQuery,
            processedEventStore,
            () -> Instant.parse("2026-08-06T10:00:00Z"),
            UUID::randomUUID);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @Test
    void createsANotificationForAnApprovedAbsence() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event("absence.absence-approved.v1", Map.of("employeeId", employeeId)));

        Notification saved = singleSaved();
        assertThat(saved.type()).isEqualTo(NotificationType.ABSENCE_APPROVED);
        assertThat(saved.recipientUserId()).isEqualTo(employeeId);
        assertThat(saved.recipientEmail()).isEqualTo("empleado@acme.test");
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.emailRequired()).isTrue();
        assertThat(saved.actionPath()).isEqualTo("/absences");
    }

    @Test
    void ignoresEventTypesItDoesNotNotify() {
        listener.onEvent(event("time-tracking.workday-started.v1", Map.of("employeeId", employeeId)));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void doesNotCreateADuplicateWhenTheEventIsRedelivered() {
        // La entrega es at-least-once y, desde que el publicador propaga los
        // fallos, el reintento reejecuta tambien a los consumidores que ya
        // habian terminado bien.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(false);

        listener.onEvent(event("absence.absence-approved.v1", Map.of("employeeId", employeeId)));

        verify(repository, never()).save(any());
    }

    @Test
    void createsTheNotificationEvenWithoutARecipientEmail() {
        // Sin correo no hay envio, pero el aviso dentro de la aplicacion sigue
        // siendo util y no debe perderse.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.empty());

        listener.onEvent(event("corrections.correction-rejected.v1", Map.of("employeeId", employeeId)));

        assertThat(singleSaved().recipientEmail()).isNull();
        assertThat(singleSaved().isDeliverable()).isFalse();
    }

    @Test
    void skipsEventsWithoutARecipientWithoutConsumingTheClaim() {
        listener.onEvent(event("corrections.correction-approved.v1", Map.of()));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void acceptsARecipientSerialisedAsText() {
        // El payload viaja como JSON en outbox_message, asi que al releerlo los
        // UUID llegan como cadenas y no como UUID.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event("corrections.correction-approved.v1", Map.of("employeeId", employeeId.toString())));

        assertThat(singleSaved().recipientUserId()).isEqualTo(employeeId);
    }

    @Test
    void translatesTheDetectedAnomaliesInTheBody() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event(
                "time-tracking.workday-anomaly-detected.v1",
                Map.of("employeeId", employeeId, "anomalies", "REQUIRED_BREAK_NOT_MET")));

        assertThat(saved()).anySatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.WORKDAY_ANOMALY_DETECTED);
            // Traducida, y sin el codigo ni los corchetes del toString() de la lista.
            assertThat(notification.body()).contains("no se alcanzó la pausa mínima obligatoria");
            assertThat(notification.body()).doesNotContain("REQUIRED_BREAK_NOT_MET").doesNotContain("[");
        });
    }

    @Test
    void namesThePersonWhoCausedTheFact() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findDisplayName(tenantId, employeeId)).thenReturn(Optional.of("Ana García"));
        UUID adminId = UUID.randomUUID();
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(new NotificationRecipient(adminId, "admin@acme.test")));

        listener.onEvent(event(
                "absence.absence-requested.v1",
                Map.of("employeeId", employeeId, "startDate", "2026-10-01", "endDate", "2026-10-03")));

        Notification saved = singleSaved();
        assertThat(saved.body()).startsWith("Ana García ha solicitado");
        assertThat(saved.body()).contains("del 1 al 3 de octubre de 2026");
        assertThat(saved.body()).doesNotContain(employeeId.toString()).doesNotContain("2026-10-01");
    }

    @Test
    void fallsBackToAGenericNameWhenThePersonCannotBeResolved() {
        // Un empleado borrado, o de otro tenant, no debe dejar un hueco ni un
        // UUID en mitad de la frase.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findDisplayName(tenantId, employeeId)).thenReturn(Optional.empty());
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(new NotificationRecipient(UUID.randomUUID(), "admin@acme.test")));

        listener.onEvent(event("absence.absence-requested.v1", Map.of("employeeId", employeeId)));

        assertThat(singleSaved().body()).startsWith("Un empleado ha solicitado");
    }

    @Test
    void resolvesEachNameOnlyOncePerEvent() {
        // Dos plantillas del mismo evento nombran al mismo empleado, y el fan-out
        // multiplica los destinatarios: sin memoria, una consulta por cada uno.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));
        when(directory.findDisplayName(tenantId, employeeId)).thenReturn(Optional.of("Ana García"));
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(
                        new NotificationRecipient(UUID.randomUUID(), "a1@acme.test"),
                        new NotificationRecipient(UUID.randomUUID(), "a2@acme.test")));

        listener.onEvent(event(
                "time-tracking.workday-anomaly-detected.v1",
                Map.of("employeeId", employeeId, "anomalies", List.of("REQUIRED_BREAK_NOT_MET"))));

        assertThat(saved()).hasSize(3);
        verify(directory, times(1)).findDisplayName(tenantId, employeeId);
    }

    @Test
    void notifiesEveryActiveAdminOfTheTenant() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        UUID firstAdmin = UUID.randomUUID();
        UUID secondAdmin = UUID.randomUUID();
        UUID thirdAdmin = UUID.randomUUID();
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(
                        new NotificationRecipient(firstAdmin, "a1@acme.test"),
                        new NotificationRecipient(secondAdmin, "a2@acme.test"),
                        new NotificationRecipient(thirdAdmin, "a3@acme.test")));

        listener.onEvent(event(
                "corrections.correction-requested.v1",
                Map.of("correctionId", UUID.randomUUID(), "requestedBy", employeeId)));

        assertThat(saved()).hasSize(3).allSatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.CORRECTION_REQUESTED);
            assertThat(notification.actionPath()).isEqualTo("/admin/corrections");
            assertThat(notification.emailRequired()).isTrue();
        });
        verify(processedEventStore, times(1)).tryClaim(any(), any());
    }

    @Test
    void doesNotNotifyTheRequesterThroughTheAdminTemplate() {
        // Quien pide la correccion ya sabe que la ha pedido, aunque ademas sea
        // administrador.
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        UUID otherAdmin = UUID.randomUUID();
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(
                        new NotificationRecipient(employeeId, "yo@acme.test"),
                        new NotificationRecipient(otherAdmin, "otro@acme.test")));

        listener.onEvent(event("corrections.correction-requested.v1", Map.of("requestedBy", employeeId)));

        assertThat(saved()).singleElement().satisfies(notification ->
                assertThat(notification.recipientUserId()).isEqualTo(otherAdmin));
    }

    @Test
    void producesBothSetsOfRecipientsWhenAnEventHasTwoTemplates() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));
        UUID adminId = UUID.randomUUID();
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(new NotificationRecipient(adminId, "admin@acme.test")));

        listener.onEvent(event(
                "time-tracking.workday-anomaly-detected.v1",
                Map.of("employeeId", employeeId, "anomalies", "MISSING_CLOCK_OUT")));

        List<Notification> saved = saved();
        assertThat(saved).hasSize(2);
        assertThat(saved).anySatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.WORKDAY_ANOMALY_DETECTED);
            assertThat(notification.recipientUserId()).isEqualTo(employeeId);
            assertThat(notification.emailRequired()).isTrue();
        });
        // La del administrador es in-app y sin correo: en un tenant grande las
        // anomalias diarias convertirian su buzon en ruido.
        assertThat(saved).anySatisfy(notification -> {
            assertThat(notification.type()).isEqualTo(NotificationType.TEAM_WORKDAY_ANOMALY);
            assertThat(notification.recipientUserId()).isEqualTo(adminId);
            assertThat(notification.emailRequired()).isFalse();
            assertThat(notification.isDeliverable()).isFalse();
        });
        verify(processedEventStore, times(1)).tryClaim(any(), any());
    }

    @Test
    void doesNotFailNorConsumeTheClaimWhenThereIsNoOneToNotify() {
        // Un tenant sin administradores activos no debe bloquear el mensaje del
        // outbox, y si mas adelante hay uno, la reentrega debe poder avisarle.
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN")).thenReturn(List.of());

        listener.onEvent(event("absence.absence-requested.v1", Map.of("employeeId", employeeId)));

        verify(repository, never()).save(any());
        verify(processedEventStore, never()).tryClaim(any(), any());
    }

    @Test
    void notifiesThePlatformAdminsOfAVerifiedRegistration() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        UUID platformAdminId = UUID.randomUUID();
        when(roleQuery.findActivePlatformAdmins())
                .thenReturn(List.of(new NotificationRecipient(platformAdminId, "plataforma@tfp.test")));

        listener.onEvent(new IntegrationEvent(
                UUID.randomUUID(),
                "tenant.registration-email-verified.v1",
                1,
                Instant.parse("2026-08-06T09:00:00Z"),
                PlatformTenant.ID,
                UUID.randomUUID(),
                "TenantRegistration",
                Map.of("registrationId", UUID.randomUUID(), "email", "alta@acme.test", "companyName", "ACME S.L.")));

        Notification saved = singleSaved();
        assertThat(saved.type()).isEqualTo(NotificationType.REGISTRATION_PENDING_REVIEW);
        assertThat(saved.tenantId()).isEqualTo(PlatformTenant.ID);
        assertThat(saved.recipientUserId()).isEqualTo(platformAdminId);
        assertThat(saved.body()).contains("ACME S.L.");
        assertThat(saved.actionPath()).isEqualTo("/platform/registrations");
    }

    @Test
    void notifiesTheEmployeeOfTheirNewAccountWithoutCredentials() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("nuevo@acme.test"));

        listener.onEvent(event(
                "identity.employee-created.v1",
                Map.of("employeeId", employeeId, "email", "nuevo@acme.test", "roles", List.of("EMPLOYEE"))));

        Notification saved = singleSaved();
        assertThat(saved.type()).isEqualTo(NotificationType.ACCOUNT_CREATED);
        assertThat(saved.actionPath()).isEqualTo("/auth/recuperar-contrasena");
        assertThat(saved.body()).doesNotContain("contraseña:").doesNotContain("password");
    }

    @Test
    void notifiesTheAdminsWhenTheirTenantIsSuspended() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        UUID adminId = UUID.randomUUID();
        when(roleQuery.findActiveByRole(tenantId, "TENANT_ADMIN"))
                .thenReturn(List.of(new NotificationRecipient(adminId, "admin@acme.test")));

        listener.onEvent(event("tenant.suspended.v1", Map.of("tenantId", tenantId, "reason", "Impago")));

        Notification saved = singleSaved();
        assertThat(saved.type()).isEqualTo(NotificationType.TENANT_SUSPENDED);
        assertThat(saved.recipientUserId()).isEqualTo(adminId);
        assertThat(saved.emailRequired()).isTrue();
        assertThat(saved.body()).contains("Impago");
    }

    @Test
    void notifiesTheEmployeeOfAnAssignedShift() {
        when(processedEventStore.tryClaim(any(), any())).thenReturn(true);
        when(directory.findEmail(tenantId, employeeId)).thenReturn(Optional.of("empleado@acme.test"));

        listener.onEvent(event(
                "shift.shift-assigned.v1",
                Map.of(
                        "employeeId", employeeId,
                        "shiftTemplateId", UUID.randomUUID(),
                        "shiftTemplateName", "Turno de mañana",
                        "validFrom", "2026-09-01",
                        "validTo", "2026-09-30")));

        Notification saved = singleSaved();
        assertThat(saved.type()).isEqualTo(NotificationType.SHIFT_ASSIGNED);
        assertThat(saved.body()).contains("«Turno de mañana»");
        // Fechas en castellano, no en ISO, y el mes no se repite.
        assertThat(saved.body()).contains("del 1 al 30 de septiembre de 2026").doesNotContain("2026-09");
        assertThat(saved.actionPath()).isEqualTo("/shifts");
    }

    private Notification singleSaved() {
        List<Notification> saved = saved();
        assertThat(saved).hasSize(1);
        return saved.get(0);
    }

    private List<Notification> saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    private IntegrationEvent event(String eventType, Map<String, Object> payload) {
        return new IntegrationEvent(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.parse("2026-08-06T09:00:00Z"),
                tenantId,
                UUID.randomUUID(),
                "Aggregate",
                payload);
    }
}
