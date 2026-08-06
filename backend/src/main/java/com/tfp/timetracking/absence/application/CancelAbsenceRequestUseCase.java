package com.tfp.timetracking.absence.application;

import com.tfp.timetracking.absence.domain.AbsenceRequest;
import com.tfp.timetracking.absence.domain.AbsenceRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelAbsenceRequestUseCase {

    private final AbsenceRequestRepository absenceRequestRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;

    public CancelAbsenceRequestUseCase(
            AbsenceRequestRepository absenceRequestRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher) {
        this.absenceRequestRepository = absenceRequestRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public AbsenceRequest cancel(UUID absenceRequestId) {
        AbsenceRequest request = absenceRequestRepository.findById(tenantContext.currentTenantId(), absenceRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de ausencia no encontrada"));
        if (!request.employeeId().equals(tenantContext.currentUserId())) {
            throw new ResourceNotFoundException("Solicitud de ausencia no encontrada");
        }
        request.cancel(clock.now(), idGenerator);
        AbsenceRequest saved = absenceRequestRepository.save(request);
        domainEventPublisher.publish(request.pullDomainEvents());
        return saved;
    }
}
