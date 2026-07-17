package com.tfp.timetracking.corrections.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfp.timetracking.corrections.domain.CorrectionRequest;
import com.tfp.timetracking.corrections.domain.CorrectionRequestRepository;
import com.tfp.timetracking.corrections.domain.CorrectionRequestStatus;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Override
    public PagedResult<CorrectionRequest> findByTenant(UUID tenantId, CorrectionRequestStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CorrectionRequestJpaEntity> result = status == null
                ? jpaRepository.findByTenantId(tenantId, pageRequest)
                : jpaRepository.findByTenantIdAndStatus(tenantId, status.name(), pageRequest);
        return toPagedResult(result);
    }

    @Override
    public PagedResult<CorrectionRequest> findByRequestedBy(
            UUID tenantId, UUID requestedBy, CorrectionRequestStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CorrectionRequestJpaEntity> result = status == null
                ? jpaRepository.findByTenantIdAndRequestedBy(tenantId, requestedBy, pageRequest)
                : jpaRepository.findByTenantIdAndRequestedByAndStatus(tenantId, requestedBy, status.name(), pageRequest);
        return toPagedResult(result);
    }

    private PagedResult<CorrectionRequest> toPagedResult(Page<CorrectionRequestJpaEntity> page) {
        return new PagedResult<>(
                page.getContent().stream().map(entity -> CorrectionRequestMapper.toDomain(entity, objectMapper)).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
