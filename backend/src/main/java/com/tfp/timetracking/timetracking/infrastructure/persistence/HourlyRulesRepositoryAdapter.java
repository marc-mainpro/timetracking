package com.tfp.timetracking.timetracking.infrastructure.persistence;

import com.tfp.timetracking.timetracking.domain.HourlyRules;
import com.tfp.timetracking.timetracking.domain.HourlyRulesRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class HourlyRulesRepositoryAdapter implements HourlyRulesRepository {

    private final HourlyRulesJpaRepository jpaRepository;

    public HourlyRulesRepositoryAdapter(HourlyRulesJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public HourlyRules save(HourlyRules rules) {
        HourlyRulesJpaEntity saved = jpaRepository.save(new HourlyRulesJpaEntity(
                rules.tenantId(),
                rules.maxDailyWork() != null ? Math.toIntExact(rules.maxDailyWork().toMinutes()) : null,
                rules.requiredBreak() != null ? Math.toIntExact(rules.requiredBreak().toMinutes()) : null));
        return toDomain(saved);
    }

    @Override
    public Optional<HourlyRules> findByTenantId(UUID tenantId) {
        return jpaRepository.findById(tenantId).map(this::toDomain);
    }

    private HourlyRules toDomain(HourlyRulesJpaEntity entity) {
        return new HourlyRules(
                entity.getTenantId(),
                entity.getMaxDailyWorkMinutes() != null ? Duration.ofMinutes(entity.getMaxDailyWorkMinutes()) : null,
                entity.getRequiredBreakMinutes() != null ? Duration.ofMinutes(entity.getRequiredBreakMinutes()) : null);
    }
}
