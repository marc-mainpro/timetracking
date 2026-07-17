package com.tfp.timetracking.corrections.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CorrectionRequestJpaRepository extends JpaRepository<CorrectionRequestJpaEntity, UUID> {

    Optional<CorrectionRequestJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select correction
            from CorrectionRequestJpaEntity correction
            where correction.tenantId = :tenantId
              and correction.workdayId = :workdayId
              and correction.requestedBy = :requestedBy
              and correction.status = 'PENDING'
            """)
    Optional<CorrectionRequestJpaEntity> findPendingByWorkdayAndRequestedBy(
            @Param("tenantId") UUID tenantId,
            @Param("workdayId") UUID workdayId,
            @Param("requestedBy") UUID requestedBy);
}
