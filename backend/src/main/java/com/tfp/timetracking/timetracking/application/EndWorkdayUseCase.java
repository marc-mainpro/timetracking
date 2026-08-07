package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayNotOpenException;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndWorkdayUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;
    private final EvaluateClosedWorkdayService evaluateClosedWorkdayService;

    public EndWorkdayUseCase(
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher,
            EvaluateClosedWorkdayService evaluateClosedWorkdayService) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
        this.evaluateClosedWorkdayService = evaluateClosedWorkdayService;
    }

    @Transactional
    public Workday endWorkday() {
        Workday workday = activeWorkday();
        workday.close(clock.now(), idGenerator);
        Workday saved = workdayRepository.save(workday);
        java.util.List<Object> events = new java.util.ArrayList<>(workday.pullDomainEvents());
        events.addAll(evaluateClosedWorkdayService.evaluate(saved));
        domainEventPublisher.publish(events);
        return saved;
    }

    private Workday activeWorkday() {
        return workdayRepository.findActiveByEmployee(tenantContext.currentTenantId(), tenantContext.currentUserId())
                .orElseThrow(WorkdayNotOpenException::new);
    }
}
