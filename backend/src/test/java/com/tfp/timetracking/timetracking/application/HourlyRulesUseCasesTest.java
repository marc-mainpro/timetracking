package com.tfp.timetracking.timetracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HourlyRulesUseCasesTest {

    @Test
    void getReturnsDefaultWhenNoRulesExist() {
        HourlyRulesRepository repository = org.mockito.Mockito.mock(HourlyRulesRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(repository.findByTenantId(tenantId)).thenReturn(java.util.Optional.empty());

        HourlyRules rules = new GetHourlyRulesUseCase(repository, tenantContext).get();

        assertThat(rules.tenantId()).isEqualTo(tenantId);
        assertThat(rules.maxDailyWork()).isNull();
        assertThat(rules.requiredBreak()).isNull();
        assertThat(rules.roundingStep()).isNull();
        assertThat(rules.tolerance()).isNull();
    }

    @Test
    void updatePersistsRulesForCurrentTenant() {
        HourlyRulesRepository repository = org.mockito.Mockito.mock(HourlyRulesRepository.class);
        TenantContext tenantContext = org.mockito.Mockito.mock(TenantContext.class);
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.currentTenantId()).thenReturn(tenantId);
        when(repository.save(any(HourlyRules.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HourlyRules saved = new UpdateHourlyRulesUseCase(repository, tenantContext).update(480, 30, 15, 5);

        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.maxDailyWork()).isEqualTo(Duration.ofHours(8));
        assertThat(saved.requiredBreak()).isEqualTo(Duration.ofMinutes(30));
        assertThat(saved.roundingStep()).isEqualTo(Duration.ofMinutes(15));
        assertThat(saved.tolerance()).isEqualTo(Duration.ofMinutes(5));
        verify(repository).save(any(HourlyRules.class));
    }
}
