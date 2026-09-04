package com.curtinhonestly.backend.dto;

/**
 * One resource as shown on the public unit page. {@code kind} is the enum name
 * (for grouping and icons), {@code kindLabel} the display name, and
 * {@code scopeLabel} says why the link is on this page ("This unit",
 * "All COMP units", "Science and Engineering", "All units").
 */
public record UnitResourceLinkDTO(
        String id,
        String title,
        String url,
        String description,
        String kind,
        String kindLabel,
        String scopeLabel
) {}
