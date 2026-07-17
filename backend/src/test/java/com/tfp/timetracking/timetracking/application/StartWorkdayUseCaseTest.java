package com.tfp.timetracking.timetracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.identity.domain.TenantAccessRepository;
import com.tfp.timetracking.identity.domain.TenantInactiveException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import com.tfp.timetracking.shared.domain.DomainEventPublisher;
import com.tfp.timetracking.shared.domain.IdGenerator;
import com.tfp.timetracking.timetracking.domain.Workday;
import com.tfp.timetracking.timetracking.domain.WorkdayAlreadyOpenException;
import com.tfp.timetracking.timetracking.domain.WorkdayRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StartWorkdayUseCaseTest {

    private final WorkdayRepository workdayRepository = org.mockito.Mockito.mock(WorkdayRepository.class);
    private final TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
    private final TenantAccessRepository tenantAccessRepository = org.mockito.Mockito.mock(TenantAccessRepository.class);
    private final DomainEventPublisher domainEventPublisher = org.mockito.Mockito.mock(DomainEventPublisher.class);
    private final Clock clock = () -> Instant.parse("2026-01-15T09:00:00Z");
    private final IdGenerator idGenerator = () -> UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void startsWorkdayForAuthenticatedEmployee() {
        StartWorkdayUseCase useCase = new StartWorkdayUseCase(
                workdayRepository, tenantContext, tenantAccessRepository, domainEventPublisher, clock, idGenerator);
        when(tenantContext.currentTenantId()).thenReturn(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        when(tenantContext.currentUserId()).thenReturn(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        when(tenantAccessRepository.isActive(any())).thenReturn(true);
        when(workdayRepository.findActiveByEmployee(any(), any())).thenReturn(java.util.Optional.empty());
        when(workdayRepository.save(any(Workday.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Workday result = useCase.start();

        assertThat(result.status().name()).isEqualTo("OPEN");
        verify(workdayRepository).save(any(Workday.class));
        verify(domainEventPublisher).publish(any(List.class));
    }

    @Test
    void rejectsWhenWorkdayAlreadyOpen() {
        StartWorkdayUseCase useCase = new StartWorkdayUseCase(
                workdayRepository, tenantContext, tenantAccessRepository, domainEventPublisher, clock, idGenerator);
        when(tenantContext.currentTenantId()).thenReturn(UUID.randomUUID());
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(tenantAccessRepository.isActive(any())).thenReturn(true);
        when(workdayRepository.findActiveByEmployee(any(), any())).thenReturn(java.util.Optional.of(org.mockito.Mockito.mock(Workday.class)));

        assertThatThrownBy(useCase::start).isInstanceOf(WorkdayAlreadyOpenException.class);
    }

    @Test
    void rejectsWhenTenantInactive() {
        StartWorkdayUseCase useCase = new StartWorkdayUseCase(
                workdayRepository, tenantContext, tenantAccessRepository, domainEventPublisher, clock, idGenerator);
        when(tenantContext.currentTenantId()).thenReturn(UUID.randomUUID());
        when(tenantContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(tenantAccessRepository.isActive(any())).thenReturn(false);

        assertThatThrownBy(useCase::start).isInstanceOf(TenantInactiveException.class);
    }
}
