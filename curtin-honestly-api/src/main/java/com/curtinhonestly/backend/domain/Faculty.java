package com.curtinhonestly.backend.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Faculty {
    BUSINESS_AND_LAW("Business and Law"),
    HEALTH_SCIENCES("Health Sciences"),
    HUMANITIES("Humanities"),
    SCIENCE_AND_ENGINEERING("Science and Engineering"),
    ABORIGINAL_STUDIES("Aboriginal Studies");

    private final String displayName;

    @JsonValue
    @Override
    public String toString() {
        return displayName;
    }
}
