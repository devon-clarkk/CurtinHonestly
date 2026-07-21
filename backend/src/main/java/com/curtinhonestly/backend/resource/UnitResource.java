package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.dto.ReviewDTO;
import com.curtinhonestly.backend.dto.TipCreateRequest;
import com.curtinhonestly.backend.dto.TipDTO;
import com.curtinhonestly.backend.dto.UnitCreateRequest;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.mapper.ReviewMapper;
import com.curtinhonestly.backend.mapper.TipMapper;
import com.curtinhonestly.backend.service.ReviewLikeService;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.service.UnitService;
import com.curtinhonestly.backend.service.UnitTipService;
import com.curtinhonestly.backend.security.SecurityConstants;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    private final ReviewLikeService reviewLikeService;
    private final UnitTipService unitTipService;

    @PostMapping
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<?> createUnit(@Valid @RequestBody UnitCreateRequest request) {
        Unit savedUnit = unitService.createUnit(request);
        return ResponseEntity.created(URI.create("/units/" + savedUnit.getId()))
                .body(savedUnit);
    }

    @GetMapping
    public ResponseEntity<?> getUnits(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String search,
                                                         @RequestParam(required = false) List<Faculty> faculties,
                                                         @RequestParam(required = false) UnitLevel level,
                                                         @RequestParam(required = false) String sortBy) {
        return ResponseEntity.ok(unitService.getAllUnits(page, size, search, faculties, level, sortBy));
    }

    @GetMapping("/{code}")
    public ResponseEntity<UnitDetailsDTO> getUnit(@PathVariable String code) {
        UnitDetailsDTO details = unitService.getUnitDetailsDTOByCode(code);
        reviewLikeService.enrichReviewsWithCurrentUserLikes(details.getReviews());
        return ResponseEntity.ok().body(details);
    }

    @GetMapping("/{unitCode}/reviews")
    public ResponseEntity<List<ReviewDTO>> getReviewsForUnit(@PathVariable String unitCode) {
        List<ReviewDTO> reviews = ReviewMapper.mapToDTOs(reviewService.getReviewsByUnitCode(unitCode));
        reviewLikeService.enrichReviewsWithCurrentUserLikes(reviews);
        return ResponseEntity.ok(reviews);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
    public ResponseEntity<?> deleteUnit(@PathVariable String id) {
        unitService.deleteUnitById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{unitCode}/tips")
    public ResponseEntity<List<TipDTO>> getTips(@PathVariable String unitCode) {
        String currentUserId = unitTipService.currentUserIdIfAuthenticated().orElse(null);
        List<TipDTO> tips = TipMapper.mapToDTOs(unitTipService.getTipsForUnit(unitCode), currentUserId);
        return ResponseEntity.ok(tips);
    }

    @PostMapping("/{unitCode}/tips")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<TipDTO> createTip(@PathVariable String unitCode, @Valid @RequestBody TipCreateRequest request) {
        UnitTip tip = unitTipService.createTip(unitCode, request.text());
        String currentUserId = unitTipService.currentUserIdIfAuthenticated().orElse(null);
        return ResponseEntity.created(URI.create("/units/" + unitCode + "/tips/" + tip.getId()))
                .body(TipMapper.mapToDTO(tip, currentUserId));
    }

    @DeleteMapping("/{unitCode}/tips/{tipId}")
    @PreAuthorize(SecurityConstants.IS_ADMIN_OR_TIP_OWNER)
    public ResponseEntity<?> deleteTip(@PathVariable String unitCode, @PathVariable String tipId) {
        unitTipService.deleteTip(tipId);
        return ResponseEntity.noContent().build();
    }

}
