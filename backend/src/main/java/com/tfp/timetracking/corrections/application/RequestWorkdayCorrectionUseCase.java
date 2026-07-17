package com.tfp.timetracking.corrections.application;

import com.tfp.timetracking.corrections.domain.CorrectionAlreadyPendingException;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestWorkdayCorrectionUseCase {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;

    public RequestWorkdayCorrectionUseCase(
            CorrectionRequestRepository correctionRequestRepository,
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher) {
        this.correctionRequestRepository = correctionRequestRepository;
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CorrectionRequest request(RequestWorkdayCorrectionCommand command) {
        UUID tenantId = tenantContext.currentTenantId();
        UUID currentUserId = tenantContext.currentUserId();
        Workday workday = workdayRepository.findById(tenantId, command.workdayId())
                .orElseThrow(() -> new ResourceNotFoundException("Jornada no encontrada"));
        if (!workday.employeeId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Jornada no encontrada");
        }
        if (correctionRequestRepository.findPendingByWorkdayAndRequestedBy(tenantId, workday.id(), currentUserId).isPresent()) {
            throw new CorrectionAlreadyPendingException();
        }
        CorrectionRequest correctionRequest = CorrectionRequest.request(
                tenantId,
                workday.id(),
                currentUserId,
                command.reason(),
                command.proposedChanges(),
                clock.now(),
                idGenerator);
        CorrectionRequest saved = correctionRequestRepository.save(correctionRequest);
        domainEventPublisher.publish(correctionRequest.pullDomainEvents());
        return saved;
    }
}
