package com.curtinhonestly.backend.dto;

/** One tick box on the admin Notifications page. */
public record AdminNotificationEventDTO(
        String event,
        String label,
        String description,
        boolean enabled
) {}
