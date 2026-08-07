package com.tfp.timetracking.notification.interfaces.rest;

import com.tfp.timetracking.notification.domain.Notification;
import com.tfp.timetracking.shared.domain.PagedResult;
import org.springframework.stereotype.Component;

/**
 * Traduce el agregado a su DTO.
 *
 * <p>Deliberadamente no expone {@code recipientEmail}, {@code status},
 * {@code attempts} ni {@code lastError}: son detalles de la entrega por correo
 * que no aportan nada al destinatario y, en el caso del correo, serian un dato
 * personal repetido innecesariamente en cada respuesta.
 */
@Component
public class NotificationRestMapper {

    public PagedNotificationsResponse toPagedResponse(PagedResult<Notification> result) {
        return new PagedNotificationsResponse(
                result.content().stream().map(this::toResponse).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type().name(),
                notification.title(),
                notification.body(),
                notification.createdAt(),
                notification.readAt(),
                notification.isRead());
    }
}
