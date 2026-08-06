package com.tfp.timetracking.notification.interfaces.rest;

import java.util.List;

public record PagedNotificationsResponse(
        List<NotificationResponse> content, int page, int size, long totalElements, int totalPages) {}
