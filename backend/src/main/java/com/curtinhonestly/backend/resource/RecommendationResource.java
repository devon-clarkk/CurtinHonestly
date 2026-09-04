package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.RecommendationSimilarUnitsDTO;
import com.curtinhonestly.backend.dto.RecommendationUnitMatchDTO;
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
 * Recommendation endpoints. Mapped at the root so the paths can live in one
 * controller: /recommendations/** is per-user, /units/{code}/similar is public
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

    /** How well one unit fits the signed-in student; 404 when the unit does not exist. */
    @GetMapping("/recommendations/me/units/{code}")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<RecommendationUnitMatchDTO> getMyMatchForUnit(@PathVariable String code) {
        return ResponseEntity.of(recommendationService.matchForCurrentUser(code));
    }

    @GetMapping("/units/{code}/similar")
    public ResponseEntity<RecommendationSimilarUnitsDTO> getSimilarUnits(@PathVariable String code) {
        return ResponseEntity.of(recommendationService.similarUnits(code));
    }
}
