package com.tfp.timetracking.notification.interfaces.rest;

import com.tfp.timetracking.notification.application.ListOwnNotificationsUseCase;
import com.tfp.timetracking.notification.application.MarkNotificationReadUseCase;
import com.tfp.timetracking.shared.interfaces.rest.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notificaciones del usuario autenticado (T110-06, RF-NOT-001).
 *
 * <p>No lleva {@code @PreAuthorize} por rol: cualquier usuario autenticado tiene
 * sus propias notificaciones. El aislamiento lo dan el tenant y el usuario del
 * principal, nunca un identificador enviado por el cliente.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final ListOwnNotificationsUseCase listOwnNotificationsUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final NotificationRestMapper notificationRestMapper;

    public NotificationController(
            ListOwnNotificationsUseCase listOwnNotificationsUseCase,
            MarkNotificationReadUseCase markNotificationReadUseCase,
            NotificationRestMapper notificationRestMapper) {
        this.listOwnNotificationsUseCase = listOwnNotificationsUseCase;
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        this.notificationRestMapper = notificationRestMapper;
    }

    @GetMapping
    @Operation(summary = "Lista las notificaciones del usuario autenticado")
    public PagedNotificationsResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageQuery pageQuery = PageQuery.of(page, size);
        return notificationRestMapper.toPagedResponse(
                listOwnNotificationsUseCase.list(pageQuery.page(), pageQuery.size()));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Numero de notificaciones sin leer del usuario autenticado")
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(listOwnNotificationsUseCase.countUnread());
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Marca como leida una notificacion propia")
    public ResponseEntity<Void> markRead(@PathVariable UUID notificationId) {
        markNotificationReadUseCase.markRead(notificationId);
        return ResponseEntity.noContent().build();
    }
}
