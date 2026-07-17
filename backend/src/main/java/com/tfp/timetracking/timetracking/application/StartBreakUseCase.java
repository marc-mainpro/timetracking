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
public class StartBreakUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final DomainEventPublisher domainEventPublisher;

    public StartBreakUseCase(
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            Clock clock,
            IdGenerator idGenerator,
            DomainEventPublisher domainEventPublisher) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public Workday startBreak() {
        Workday workday = activeWorkday();
        workday.startBreak(clock.now(), idGenerator);
        Workday saved = workdayRepository.save(workday);
        domainEventPublisher.publish(workday.pullDomainEvents());
        return saved;
    }

    private Workday activeWorkday() {
        return workdayRepository.findActiveByEmployee(tenantContext.currentTenantId(), tenantContext.currentUserId())
                .orElseThrow(WorkdayNotOpenException::new);
    }
}
