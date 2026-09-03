package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationsDTO;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.recommendation.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recommendation endpoints. Mapped at the root so the two paths can live in one
 * controller: /recommendations/me is per-user, /units/{code}/similar is public
 * and more specific than the /units/{code} handlers in UnitResource.
 */
@RestController
@RequiredArgsConstructor
public class RecommendationResource {

    private final RecommendationService recommendationService;

    @GetMapping("/recommendations/me")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<RecommendationsDTO> getMyRecommendations() {
        return ResponseEntity.ok(recommendationService.recommendationsForCurrentUser());
    }

    @GetMapping("/units/{code}/similar")
    public ResponseEntity<RecommendationSimilarUnitsDTO> getSimilarUnits(@PathVariable String code) {
        return ResponseEntity.of(recommendationService.similarUnits(code));
    }
}
