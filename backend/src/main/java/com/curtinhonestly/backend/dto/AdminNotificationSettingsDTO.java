package com.curtinhonestly.backend.dto;

import java.util.List;

/**
 * GET /admin/notifications. {@code mailConfigured} tells the dashboard whether this
 * environment can actually deliver (SMTP host set) or only logs would-be sends.
 * {@code customised} is false while the environment default is still in force.
 */
public record AdminNotificationSettingsDTO(
        String recipientEmail,
        boolean mailConfigured,
        boolean customised,
        List<AdminNotificationEventDTO> events
) {}
