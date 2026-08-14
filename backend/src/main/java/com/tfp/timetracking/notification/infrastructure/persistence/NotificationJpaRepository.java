package com.tfp.timetracking.notification.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<NotificationJpaEntity> findByTenantIdAndRecipientUserId(UUID tenantId, UUID recipientUserId, Pageable pageable);

    long countByTenantIdAndRecipientUserIdAndReadAtIsNull(UUID tenantId, UUID recipientUserId);

    long countByStatus(String status);

    @Query("""
            select n from NotificationJpaEntity n
            where n.status = 'PENDING' and n.emailRequired = true and n.recipientEmail is not null
            order by n.createdAt asc
            """)
    List<NotificationJpaEntity> findPendingForDelivery(Pageable pageable);

    @Query("""
            select count(n) from NotificationJpaEntity n
            where n.status = 'PENDING' and n.emailRequired = true and n.recipientEmail is not null
            """)
    long countPendingForDelivery();
}
