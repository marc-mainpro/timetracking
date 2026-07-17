package com.tfp.timetracking.timetracking.application;

import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayAlreadyOpenException;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StartWorkdayUseCase {

    private final WorkdayRepository workdayRepository;
    private final TenantContext tenantContext;
    private final TenantAccessRepository tenantAccessRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public StartWorkdayUseCase(
            WorkdayRepository workdayRepository,
            TenantContext tenantContext,
            TenantAccessRepository tenantAccessRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock,
            IdGenerator idGenerator) {
        this.workdayRepository = workdayRepository;
        this.tenantContext = tenantContext;
        this.tenantAccessRepository = tenantAccessRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public Workday start() {
        var tenantId = tenantContext.currentTenantId();
        var userId = tenantContext.currentUserId();
        if (!tenantAccessRepository.isActive(tenantId)) {
            throw new TenantInactiveException();
        }
        if (workdayRepository.findActiveByEmployee(tenantId, userId).isPresent()) {
            throw new WorkdayAlreadyOpenException();
        }
        Workday workday = Workday.start(tenantId, userId, clock.now(), idGenerator);
        Workday saved = workdayRepository.save(workday);
        domainEventPublisher.publish(workday.pullDomainEvents());
        return saved;
    }
}
