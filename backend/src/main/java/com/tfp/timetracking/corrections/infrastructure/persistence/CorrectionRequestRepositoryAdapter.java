package com.tfp.timetracking.corrections.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class CorrectionRequestRepositoryAdapter implements CorrectionRequestRepository {

    private final CorrectionRequestJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public CorrectionRequestRepositoryAdapter(CorrectionRequestJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public CorrectionRequest save(CorrectionRequest correctionRequest) {
        return CorrectionRequestMapper.toDomain(
                jpaRepository.save(CorrectionRequestMapper.toJpaEntity(correctionRequest, objectMapper)), objectMapper);
    }

    @Override
    public Optional<CorrectionRequest> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByTenantIdAndId(tenantId, id).map(entity -> CorrectionRequestMapper.toDomain(entity, objectMapper));
    }

    @Override
    public Optional<CorrectionRequest> findPendingByWorkdayAndRequestedBy(UUID tenantId, UUID workdayId, UUID requestedBy) {
        return jpaRepository.findPendingByWorkdayAndRequestedBy(tenantId, workdayId, requestedBy)
                .map(entity -> CorrectionRequestMapper.toDomain(entity, objectMapper));
    }
}
