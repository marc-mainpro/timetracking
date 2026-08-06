package com.tfp.timetracking.notification.infrastructure.persistence;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationStatus;
import com.tfp.timetracking.notification.domain.NotificationType;

/** Traduccion entre el agregado y su fila. */
final class NotificationMapper {

    private NotificationMapper() {}

    static NotificationJpaEntity toJpaEntity(Notification notification) {
        return new NotificationJpaEntity(
                notification.id(),
                notification.tenantId(),
                notification.recipientUserId(),
                notification.recipientEmail(),
                notification.type().name(),
                notification.title(),
                notification.body(),
                notification.status().name(),
                notification.attempts(),
                notification.lastError(),
                notification.createdAt(),
                notification.sentAt(),
                notification.readAt());
    }

    static Notification toDomain(NotificationJpaEntity entity) {
        return Notification.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getRecipientUserId(),
                entity.getRecipientEmail(),
                NotificationType.valueOf(entity.getType()),
                entity.getTitle(),
                entity.getBody(),
                NotificationStatus.valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getReadAt());
    }
}
