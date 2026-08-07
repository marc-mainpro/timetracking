package com.tfp.timetracking.absence.application;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestAbsenceUseCase {

    private final AbsenceTypeRepository absenceTypeRepository;
    private final AbsenceRequestRepository absenceRequestRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;

    public RequestAbsenceUseCase(
            AbsenceTypeRepository absenceTypeRepository,
            AbsenceRequestRepository absenceRequestRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher) {
        this.absenceTypeRepository = absenceTypeRepository;
        this.absenceRequestRepository = absenceRequestRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public AbsenceRequest request(RequestAbsenceCommand command) {
        AbsenceType absenceType = absenceTypeRepository
                .findById(tenantContext.currentTenantId(), command.absenceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de ausencia no encontrado"));
        if (!absenceType.active()) {
            throw new InactiveAbsenceTypeException();
        }
        AbsenceRequest request = AbsenceRequest.request(
                tenantContext.currentTenantId(),
                tenantContext.currentUserId(),
                absenceType.id(),
                command.startDate(),
                command.endDate(),
                command.reason(),
                clock.now(),
                idGenerator);
        AbsenceRequest saved = absenceRequestRepository.save(request);
        domainEventPublisher.publish(request.pullDomainEvents());
        return saved;
    }
}
