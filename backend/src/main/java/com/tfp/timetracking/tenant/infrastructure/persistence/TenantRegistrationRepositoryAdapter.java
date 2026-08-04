package com.tfp.timetracking.tenant.infrastructure.persistence;

import com.tfp.timetracking.shared.domain.PagedResult;
import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationRepository;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Adaptador JPA del puerto {@link TenantRegistrationRepository}. */
@Repository
public class TenantRegistrationRepositoryAdapter implements TenantRegistrationRepository {

    private static final List<String> OPEN_STATUSES = List.of(
            TenantRegistrationStatus.PENDING_EMAIL_VERIFICATION.name(),
            TenantRegistrationStatus.PENDING_REVIEW.name(),
            TenantRegistrationStatus.APPROVED.name());

    private final TenantRegistrationJpaRepository jpaRepository;

    public TenantRegistrationRepositoryAdapter(TenantRegistrationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TenantRegistration save(TenantRegistration registration) {
        return TenantRegistrationMapper.toDomain(
                jpaRepository.save(TenantRegistrationMapper.toJpaEntity(registration)));
    }

    @Override
    public Optional<TenantRegistration> findById(UUID id) {
        return jpaRepository.findById(id).map(TenantRegistrationMapper::toDomain);
    }

    @Override
    public Optional<TenantRegistration> findByVerificationTokenHash(String verificationTokenHash) {
        if (verificationTokenHash == null || verificationTokenHash.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findByVerificationTokenHash(verificationTokenHash).map(TenantRegistrationMapper::toDomain);
    }

    @Override
    public Optional<TenantRegistration> findOpenByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findByEmailAndStatusInOrderByCreatedAtDesc(email, OPEN_STATUSES).stream()
                .findFirst()
                .map(TenantRegistrationMapper::toDomain);
    }

    @Override
    public PagedResult<TenantRegistration> findAll(TenantRegistrationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TenantRegistrationJpaEntity> result =
                status == null ? jpaRepository.findAll(pageable) : jpaRepository.findByStatus(status.name(), pageable);
        return new PagedResult<>(
                result.getContent().stream().map(TenantRegistrationMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public long countByIpHashSince(String ipHash, Instant since) {
        if (ipHash == null || ipHash.isBlank()) {
            return 0;
        }
        return jpaRepository.countByIpHashSince(ipHash, since);
    }

    @Override
    public long countByEmailSince(String email, Instant since) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        return jpaRepository.countByEmailSince(email, since);
    }
}
