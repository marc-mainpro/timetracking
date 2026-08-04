package com.tfp.timetracking.tenant.infrastructure.persistence;

import com.tfp.timetracking.tenant.domain.TenantRegistration;
import com.tfp.timetracking.tenant.domain.TenantRegistrationStatus;

/** Mapper dominio &lt;-&gt; JPA para {@link TenantRegistration}. */
final class TenantRegistrationMapper {

    private TenantRegistrationMapper() {}

    static TenantRegistrationJpaEntity toJpaEntity(TenantRegistration registration) {
        return new TenantRegistrationJpaEntity(
                registration.id(),
                registration.companyName(),
                registration.ownerFirstName(),
                registration.ownerLastName(),
                registration.email(),
                registration.ownerPasswordHash(),
                registration.timezone(),
                registration.status().name(),
                registration.verificationTokenHash(),
                registration.verificationTokenExpiresAt(),
                registration.verificationSentAt(),
                registration.resendCount(),
                registration.source(),
                registration.ipHash(),
                registration.decisionReason(),
                registration.createdTenantId(),
                registration.createdAt(),
                registration.updatedAt(),
                registration.verifiedAt(),
                registration.decidedAt());
    }

    static TenantRegistration toDomain(TenantRegistrationJpaEntity entity) {
        return TenantRegistration.reconstitute(
                entity.getId(),
                entity.getCompanyName(),
                entity.getOwnerFirstName(),
                entity.getOwnerLastName(),
                entity.getEmail(),
                entity.getOwnerPasswordHash(),
                entity.getTimezone(),
                TenantRegistrationStatus.valueOf(entity.getStatus()),
                entity.getVerificationTokenHash(),
                entity.getVerificationTokenExpiresAt(),
                entity.getVerificationSentAt(),
                entity.getResendCount(),
                entity.getSource(),
                entity.getIpHash(),
                entity.getDecisionReason(),
                entity.getCreatedTenantId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVerifiedAt(),
                entity.getDecidedAt());
    }
}
