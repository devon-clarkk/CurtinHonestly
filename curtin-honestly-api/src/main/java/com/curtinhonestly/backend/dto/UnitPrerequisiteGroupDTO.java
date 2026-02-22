package com.curtinhonestly.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class UnitPrerequisiteGroupDTO {
    private String groupName;
    private String requirement;
    private Integer position;
    private List<UnitPrerequisiteOptionDTO> options;
}
