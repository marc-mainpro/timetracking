package com.tfp.timetracking.audit.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, UUID> {

    @Query("""
            select auditEvent
            from AuditEventJpaEntity auditEvent
            where auditEvent.tenantId = :tenantId
              and (:action is null or auditEvent.action = :action)
              and auditEvent.occurredAt >= :from
              and auditEvent.occurredAt <= :to
            """)
    Page<AuditEventJpaEntity> findByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("action") String action,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
