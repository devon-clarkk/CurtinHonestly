package com.curtinhonestly.backend.domain;

import lombok.Getter;

@Getter
public enum UnitLevel {
    UNDERGRADUATE("Undergraduate"),
    POSTGRADUATE("Postgraduate");

    private final String displayName;

    UnitLevel(String displayName) {
        this.displayName = displayName;
    }
}
