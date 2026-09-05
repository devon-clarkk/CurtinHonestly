package com.curtinhonestly.backend.dto;

import java.util.List;

/** The enum vocabularies the admin form needs for its selects, as name/label pairs. */
public record AdminUnitResourceOptionsDTO(List<Option> kinds, List<Option> faculties, List<Option> levels) {
    public record Option(String value, String label) {}
}
