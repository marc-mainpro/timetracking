package com.tfp.timetracking.notification.infrastructure.persistence;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.shared.domain.PagedResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    NotificationRepositoryAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        return NotificationMapper.toDomain(jpaRepository.save(NotificationMapper.toJpaEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(UUID tenantId, UUID id) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(NotificationMapper::toDomain);
    }

    @Override
    public PagedResult<Notification> findByRecipient(UUID tenantId, UUID recipientUserId, int page, int size) {
        Page<NotificationJpaEntity> result = jpaRepository.findByTenantIdAndRecipientUserId(
                tenantId, recipientUserId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PagedResult<>(
                result.getContent().stream().map(NotificationMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public long countUnreadByRecipient(UUID tenantId, UUID recipientUserId) {
        return jpaRepository.countByTenantIdAndRecipientUserIdAndReadAtIsNull(tenantId, recipientUserId);
    }

    @Override
    public long countByStatus(com.tfp.timetracking.notification.domain.NotificationStatus status) {
        return jpaRepository.countByStatus(status.name());
    }

    @Override
    public PagedResult<Notification> findByStatus(
            com.tfp.timetracking.notification.domain.NotificationStatus status, int page, int size) {
        Page<NotificationJpaEntity> result =
                jpaRepository.findByStatusOrderByCreatedAtAsc(status.name(), PageRequest.of(page, size));
        return new PagedResult<>(
                result.getContent().stream().map(NotificationMapper::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Optional<Notification> findByIdForPlatform(UUID id) {
        return jpaRepository.findById(id).map(NotificationMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean requeueFailed(UUID id) {
        return jpaRepository.requeueFailed(id) > 0;
    }

    @Override
    @Transactional
    public boolean discardFailed(UUID id) {
        return jpaRepository.discardFailed(id) > 0;
    }

    @Override
    public long countPendingForDelivery() {
        return jpaRepository.countPendingForDelivery();
    }

    @Override
    public List<Notification> findPendingForDelivery(int limit) {
        return jpaRepository.findPendingForDelivery(PageRequest.of(0, limit)).stream()
                .map(NotificationMapper::toDomain)
                .toList();
    }
}
