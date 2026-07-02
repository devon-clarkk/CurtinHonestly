package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitSummaryDTO;
import com.curtinhonestly.backend.mapper.ReviewMapper;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.service.UnitService;
import com.curtinhonestly.backend.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/units")
@RequiredArgsConstructor
@Slf4j
public class UnitResource {

    private final UnitService unitService;
    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<?> createUnit(@RequestBody Unit unit) {
        try {
            Unit savedUnit = unitService.createUnit(unit);
            return ResponseEntity.created(URI.create("/units/" + savedUnit.getId()))
                    .body(savedUnit);
        } catch (Exception e) {
            log.error("Error creating unit via API: {}", e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Failed to create unit: " + message + "\"}");
        }
    }

    @GetMapping
    public ResponseEntity<?> getUnits(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String search,
                                                         @RequestParam(required = false) List<Faculty> faculties,
                                                         @RequestParam(required = false) UnitLevel level,
                                                         @RequestParam(required = false) String sortBy) {
        try {
            return ResponseEntity.ok(unitService.getAllUnits(page, size, search, faculties, level, sortBy));
        } catch (Exception e) {
            log.error("Error fetching units: {}", e.getMessage(), e);
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Provide a bit more context if it's a known error type
            if (e instanceof NullPointerException) message = "NullPointerException at " + e.getStackTrace()[0];
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"Failed to fetch units: " + message + "\"}");
        }
    }

    @GetMapping("/{code}")
    public ResponseEntity<UnitDetailsDTO> getUnit(@PathVariable String code) {
        return ResponseEntity.ok().body(unitService.getUnitDetailsDTOByCode(code));
    }

    @GetMapping("/{unitCode}/reviews")
    public ResponseEntity<List<ReviewDTO>> getReviewsForUnit(@PathVariable String unitCode) {
        return ResponseEntity.ok(ReviewMapper.mapToDTOs(reviewService.getReviewsByUnitCode(unitCode)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<?> deleteUnit(@PathVariable String id) {
        unitService.deleteUnitById(id);
        return ResponseEntity.noContent().build();
    }

}
