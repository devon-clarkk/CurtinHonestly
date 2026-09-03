package com.curtinhonestly.backend.service.recommendation;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.UnitLevel;

/**
 * Catalogue facts about a unit the recommender needs to label and group results.
 * averageRating is the stored aggregate and is only used as a fallback when the
 * model has no reviews of its own for the unit.
 */
public record UnitInfo(String code, String name, Faculty faculty, UnitLevel level, double averageRating) {

    public String facultyLabel() {
        return faculty == null ? "" : faculty.getDisplayName();
    }

    public String levelLabel() {
        return level == null ? "" : level.getDisplayName();
    }
}
