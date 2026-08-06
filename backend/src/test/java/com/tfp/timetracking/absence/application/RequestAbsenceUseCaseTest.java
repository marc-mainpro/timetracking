package com.tfp.timetracking.absence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.absence.domain.AbsenceType;
import com.tfp.timetracking.absence.domain.AbsenceTypeRepository;
import com.tfp.timetracking.absence.domain.InactiveAbsenceTypeException;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestAbsenceUseCaseTest {

    private final AbsenceTypeRepository absenceTypeRepository = org.mockito.Mockito.mock(AbsenceTypeRepository.class);
    private final AbsenceRequestRepository absenceRequestRepository = org.mockito.Mockito.mock(AbsenceRequestRepository.class);
    private final TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
    private final Clock clock = () -> Instant.parse("2026-08-01T10:00:00Z");
    private final IdGenerator idGenerator = UUID::randomUUID;
    private final DomainEventPublisher domainEventPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);

    private RequestAbsenceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RequestAbsenceUseCase(
                absenceTypeRepository, absenceRequestRepository, tenantContext, clock, idGenerator, domainEventPublisher);
    }

    @Test
    void createsRequestWhenTypeExistsAndIsActive() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(userId);
        when(absenceTypeRepository.findById(tenantId, typeId))
                .thenReturn(java.util.Optional.of(AbsenceType.create(tenantId, "VAC", "Vacaciones", true, false, typeId)));
        when(absenceRequestRepository.save(any(AbsenceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AbsenceRequest saved = useCase.request(new RequestAbsenceCommand(
                typeId, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), "Vacaciones"));

        assertThat(saved.employeeId()).isEqualTo(userId);
        assertThat(saved.absenceTypeId()).isEqualTo(typeId);
        verify(domainEventPublisher).publish(any());
    }

    @Test
    void rejectsUnknownOrInactiveType() {
        UUID tenantId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);

        when(absenceTypeRepository.findById(tenantId, typeId)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> useCase.request(new RequestAbsenceCommand(
                        typeId, LocalDate.now(), LocalDate.now(), null)))
                .isInstanceOf(ResourceNotFoundException.class);

        when(absenceTypeRepository.findById(tenantId, typeId))
                .thenReturn(java.util.Optional.of(AbsenceType.reconstitute(typeId, tenantId, "VAC", "Vacaciones", true, false, false)));
        assertThatThrownBy(() -> useCase.request(new RequestAbsenceCommand(
                        typeId, LocalDate.now(), LocalDate.now(), null)))
                .isInstanceOf(InactiveAbsenceTypeException.class);

        verify(absenceRequestRepository, never()).save(any());
    }
}
