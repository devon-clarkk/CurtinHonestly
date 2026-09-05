package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.RecommendationStatsDTO;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.recommendation.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin insight into the recommendation model. Read only. */
@RestController
@RequestMapping("/admin/recommendations")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminRecommendationResource {

    private final RecommendationService recommendationService;

    @GetMapping("/stats")
    public ResponseEntity<RecommendationStatsDTO> getStats() {
        return ResponseEntity.ok(recommendationService.stats());
    }
}
