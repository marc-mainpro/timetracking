package com.tfp.timetracking.shift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.audit.application.AuditRecorder;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shift.domain.model.ShiftBreakPolicy;
import com.tfp.timetracking.shift.domain.model.ShiftTemplate;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateAlreadyExistsException;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateRepository;
import com.tfp.timetracking.shift.domain.model.ShiftTemplateStatus;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftTemplateUseCasesTest {

    private final AuditRecorder auditRecorder = org.mockito.Mockito.mock(AuditRecorder.class);

    @Test
    void createsTemplateForCurrentTenantAndRejectsDuplicateName() {
        ShiftTemplateRepository repository = org.mockito.Mockito.mock(ShiftTemplateRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(repository.findByName(tenantId, "General")).thenReturn(Optional.empty());
        when(repository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftTemplate saved = new CreateShiftTemplateUseCase(repository, tenantContext, auditRecorder)
                .create(new SaveShiftTemplateCommand("General", LocalTime.of(8, 0), LocalTime.of(16, 0), 30));

        assertThat(saved.tenantId()).isEqualTo(tenantId);
        verify(repository).save(any(ShiftTemplate.class));

        when(repository.findByName(tenantId, "General")).thenReturn(Optional.of(saved));
        assertThatThrownBy(() -> new CreateShiftTemplateUseCase(repository, tenantContext, auditRecorder)
                        .create(new SaveShiftTemplateCommand("General", LocalTime.of(8, 0), LocalTime.of(16, 0), 30)))
                .isInstanceOf(ShiftTemplateAlreadyExistsException.class);
    }

    @Test
    void updatesAndArchivesExistingTemplate() {
        ShiftTemplateRepository repository = org.mockito.Mockito.mock(ShiftTemplateRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        ShiftTemplate template = ShiftTemplate.reconstitute(
                templateId,
                tenantId,
                "General",
                LocalTime.of(8, 0),
                LocalTime.of(16, 0),
                new ShiftBreakPolicy(Duration.ofMinutes(30)),
                ShiftTemplateStatus.ACTIVE);
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(repository.findById(tenantId, templateId)).thenReturn(Optional.of(template));
        when(repository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftTemplate updated = new UpdateShiftTemplateUseCase(repository, tenantContext, auditRecorder)
                .update(templateId, new SaveShiftTemplateCommand("Tarde", LocalTime.of(14, 0), LocalTime.of(22, 0), 15));
        assertThat(updated.name()).isEqualTo("Tarde");

        ShiftTemplate archived = new ArchiveShiftTemplateUseCase(repository, tenantContext, auditRecorder).archive(templateId);
        assertThat(archived.status()).isEqualTo(ShiftTemplateStatus.ARCHIVED);
    }

    @Test
    void auditsEveryAdministrativeChangeOnTemplates() {
        // T130-04: sin esto no queda rastro de quién creó, cambió o archivó una
        // plantilla de turno, que es información de planificación laboral.
        ShiftTemplateRepository repository = org.mockito.Mockito.mock(ShiftTemplateRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(repository.findByName(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(ShiftTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftTemplate created = new CreateShiftTemplateUseCase(repository, tenantContext, auditRecorder)
                .create(new SaveShiftTemplateCommand("Auditada", LocalTime.of(9, 0), LocalTime.of(17, 0), 30));

        verify(auditRecorder)
                .record(org.mockito.ArgumentMatchers.eq("SHIFT_TEMPLATE_CREATED"),
                        org.mockito.ArgumentMatchers.eq("ShiftTemplate"),
                        org.mockito.ArgumentMatchers.eq(created.id()),
                        any());

        when(repository.findById(tenantId, created.id())).thenReturn(Optional.of(created));
        new ArchiveShiftTemplateUseCase(repository, tenantContext, auditRecorder).archive(created.id());

        verify(auditRecorder)
                .record(org.mockito.ArgumentMatchers.eq("SHIFT_TEMPLATE_ARCHIVED"),
                        org.mockito.ArgumentMatchers.eq("ShiftTemplate"),
                        org.mockito.ArgumentMatchers.eq(created.id()),
                        any());
    }
}
