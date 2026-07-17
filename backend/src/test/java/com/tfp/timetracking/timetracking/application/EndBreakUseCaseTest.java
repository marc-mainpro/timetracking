package com.tfp.timetracking.timetracking.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EndBreakUseCaseTest {

    @Test
    void endsBreakOnActiveWorkdayAndPublishesEvent() {
        WorkdayRepository repository = org.mockito.Mockito.mock(WorkdayRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        DomainEventPublisher publisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
        Clock clock = () -> Instant.parse("2026-01-15T10:30:00Z");
        IdGenerator idGenerator = () -> UUID.randomUUID();
        EndBreakUseCase useCase = new EndBreakUseCase(repository, tenantContext, clock, idGenerator, publisher);
        Workday workday = Workday.start(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-01-15T09:00:00Z"), idGenerator);
        workday.pullDomainEvents();
        workday.startBreak(Instant.parse("2026-01-15T10:00:00Z"), idGenerator);
        workday.pullDomainEvents();
        when(tenantContext.currentTenantId()).thenReturn(workday.tenantId());
        when(tenantContext.currentUserId()).thenReturn(workday.employeeId());
        when(repository.findActiveByEmployee(workday.tenantId(), workday.employeeId())).thenReturn(java.util.Optional.of(workday));
        when(repository.save(any(Workday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.endBreak();

        verify(repository).save(any(Workday.class));
        verify(publisher).publish(any(List.class));
    }
}
