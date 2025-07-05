package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.service.UnitService;
import com.curtinhonestly.backend.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/units")
@RequiredArgsConstructor
public class UnitResource {

    private final UnitService unitService;
    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<Unit> createUnit(@RequestBody Unit unit) {
        return ResponseEntity.created(URI.create("/units/" + unit.getId()))
                .body(unitService.createUnit(unit));
    }

    @GetMapping
    public ResponseEntity<Page<Unit>> getUnits(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok().body(unitService.getAllUnits(page, size));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Unit> getUnit(@PathVariable String code) {
        return ResponseEntity.ok().body(unitService.getUnitByCode(code));
    }

    @GetMapping("/{unitCode}/reviews")
    public ResponseEntity<List<Review>> getReviewsForUnit(@PathVariable String unitCode) {
        return ResponseEntity.ok().body(reviewService.getReviewsByUnitCode(unitCode));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<?> deleteUnit(@PathVariable String id) {
        unitService.deleteUnitById(id);
        return ResponseEntity.noContent().build();
    }

}
