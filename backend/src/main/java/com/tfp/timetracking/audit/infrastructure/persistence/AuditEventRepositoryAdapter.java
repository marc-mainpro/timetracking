package com.tfp.timetracking.audit.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.audit.domain.AuditEvent;
import com.tfp.timetracking.audit.domain.AuditEventRepository;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public class AuditEventRepositoryAdapter implements AuditEventRepository {

    private static final Instant MIN_FILTER_DATE = Instant.parse("1970-01-01T00:00:00Z");
    private static final Instant MAX_FILTER_DATE = Instant.parse("9999-12-31T23:59:59Z");

    private final AuditEventJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public AuditEventRepositoryAdapter(AuditEventJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AuditEvent save(AuditEvent auditEvent) {
        return AuditEventMapper.toDomain(jpaRepository.save(AuditEventMapper.toJpaEntity(auditEvent, objectMapper)), objectMapper);
    }

    @Override
    public PagedResult<AuditEvent> findByTenant(UUID tenantId, String action, Instant from, Instant to, int page, int size) {
        Page<AuditEventJpaEntity> result = jpaRepository.findByTenant(
                tenantId,
                action,
                from != null ? from : MIN_FILTER_DATE,
                to != null ? to : MAX_FILTER_DATE,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new PagedResult<>(
                result.getContent().stream().map(entity -> AuditEventMapper.toDomain(entity, objectMapper)).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
