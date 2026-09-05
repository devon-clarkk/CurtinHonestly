package com.curtinhonestly.backend.dto;

import java.util.List;

/** The enum vocabularies the event forms need for their selects, as name/label pairs. */
public record ClubEventOptionsDTO(
        List<AdminUnitResourceOptionsDTO.Option> kinds,
        List<AdminUnitResourceOptionsDTO.Option> statuses,
        List<AdminUnitResourceOptionsDTO.Option> faculties,
        List<AdminUnitResourceOptionsDTO.Option> levels
) {}
