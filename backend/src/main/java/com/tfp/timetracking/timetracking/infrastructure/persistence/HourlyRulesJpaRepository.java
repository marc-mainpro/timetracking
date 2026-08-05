package com.tfp.timetracking.timetracking.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface HourlyRulesJpaRepository extends JpaRepository<HourlyRulesJpaEntity, UUID> {}
