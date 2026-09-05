package com.curtinhonestly.backend.dto;

/** Admin rejection of a pending event. The reason is shown back to the club in its portal. */
public record ClubEventRejectRequest(String reason) {}
