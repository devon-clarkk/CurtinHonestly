package com.curtinhonestly.backend.dto;

import lombok.Data;

@Data
public class CoursePrerequisiteOptionDTO {
    private String courseCode;
    private Integer credits;
    private String title;
    private boolean concurrent;
}
