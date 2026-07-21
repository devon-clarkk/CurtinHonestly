package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class UnitPrerequisiteGroupDTO {
    private String groupName;
    private String requirement;
    private Integer position;
    private List<UnitPrerequisiteOptionDTO> options;
    private List<CoursePrerequisiteOptionDTO> courseOptions;

    // Eligibility (roadmap 4.4), populated only for authenticated requests —
    // null for both fields when the request is anonymous. `satisfied` is
    // null (rather than false) when the group can't be fully evaluated from
    // completed-unit data alone (e.g. it includes a course-credit
    // requirement), in which case `unverifiable` is true.
    private Boolean satisfied;
    private boolean unverifiable;
}
