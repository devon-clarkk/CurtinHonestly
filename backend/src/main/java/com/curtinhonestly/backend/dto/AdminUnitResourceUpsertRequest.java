package com.curtinhonestly.backend.dto;

/**
 * Create or edit a resource from the admin app. Exactly one targeting mode is
 * used: when {@code unitCode} is non-blank the row targets that unit and the
 * rule fields are ignored; otherwise the row is a rule built from
 * {@code codePrefixes} (comma separated), {@code faculty} and {@code level}
 * (enum names or display names; blank means "not a criterion").
 *
 * {@code status} is only honoured on create (APPROVED by default, or PENDING
 * to park a draft); edits never change status, approve and reject do.
 * {@code sortOrder} is optional on both.
 */
public record AdminUnitResourceUpsertRequest(
        String title,
        String url,
        String description,
        String kind,
        String unitCode,
        String codePrefixes,
        String faculty,
        String level,
        String status,
        Integer sortOrder
) {}
