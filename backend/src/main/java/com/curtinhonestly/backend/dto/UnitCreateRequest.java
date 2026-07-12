package com.curtinhonestly.backend.dto;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.UnitLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// Only the fields an admin is allowed to set when creating/updating a unit.
// Aggregate fields (reviewCount, averageRating, etc.) and id are always
// computed/assigned server-side - never bind the Unit entity directly here.
public record UnitCreateRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotBlank String description,
        String unitLink,
        @NotNull Faculty faculty,
        @NotNull UnitLevel level,
        String area,
        String fieldOfEducation,
        Integer credits,
        Integer contactHours,
        String resultType,
        List<TuitionPattern> tuitionPatterns,
        List<PrerequisiteGroup> prerequisiteGroups
) {
    public record TuitionPattern(String type, String duration) {}

    public record PrerequisiteGroup(
            String groupName,
            String requirement,
            Integer position,
            List<PrerequisiteOption> options,
            List<CoursePrerequisiteOption> courseOptions
    ) {}

    public record PrerequisiteOption(String code, String title, boolean concurrent) {}

    public record CoursePrerequisiteOption(String courseCode, Integer credits, String title, boolean concurrent) {}
}
