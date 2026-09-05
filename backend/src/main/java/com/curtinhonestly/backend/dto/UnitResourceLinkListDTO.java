package com.curtinhonestly.backend.dto;

import java.util.List;

/** Response body of GET /units/{code}/resources. Items arrive grouped by kind, then by sort order and title. */
public record UnitResourceLinkListDTO(List<UnitResourceLinkDTO> items) {}
