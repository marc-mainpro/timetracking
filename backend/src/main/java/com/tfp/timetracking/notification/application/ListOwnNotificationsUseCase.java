package com.tfp.timetracking.notification.application;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.notification.domain.NotificationRepository;
import com.tfp.timetracking.shared.application.TenantContext;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Notificaciones del usuario autenticado (T110-06, RF-NOT-001). */
@Service
public class ListOwnNotificationsUseCase {

    private final NotificationRepository notificationRepository;
    private final TenantContext tenantContext;

    public ListOwnNotificationsUseCase(NotificationRepository notificationRepository, TenantContext tenantContext) {
        this.notificationRepository = notificationRepository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public PagedResult<Notification> list(int page, int size) {
        return notificationRepository.findByRecipient(
                tenantContext.currentTenantId(), tenantContext.currentUserId(), page, size);
    }

    @Transactional(readOnly = true)
    public long countUnread() {
        return notificationRepository.countUnreadByRecipient(
                tenantContext.currentTenantId(), tenantContext.currentUserId());
    }
}
