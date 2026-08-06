package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.shared.application.ResourceNotFoundException;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Marca como leida una notificacion propia (T110-06). */
@Service
public class MarkNotificationReadUseCase {

    private final NotificationRepository notificationRepository;
    private final TenantContext tenantContext;
    private final Clock clock;

    public MarkNotificationReadUseCase(
            NotificationRepository notificationRepository, TenantContext tenantContext, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    @Transactional
    public void markRead(UUID notificationId) {
        Notification notification = notificationRepository
                .findById(tenantContext.currentTenantId(), notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada"));
        // Una notificacion ajena se trata como inexistente: responder 403
        // confirmaria que ese identificador existe en el tenant.
        if (!notification.recipientUserId().equals(tenantContext.currentUserId())) {
            throw new ResourceNotFoundException("Notificación no encontrada");
        }
        notification.markRead(clock.now());
        notificationRepository.save(notification);
    }
}
