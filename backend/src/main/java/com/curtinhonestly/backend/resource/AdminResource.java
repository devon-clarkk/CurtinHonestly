package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.*;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.AdminService;
import com.curtinhonestly.backend.service.CampaignService;
import com.curtinhonestly.backend.service.ReviewFlagService;
import com.curtinhonestly.backend.service.ReviewService;
import com.curtinhonestly.backend.service.UnitAggregateService;
import com.curtinhonestly.backend.service.UnitRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminResource {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final UnitAggregateService unitAggregateService;
    private final CampaignService campaignService;
    private final UnitRequestService unitRequestService;
    private final ReviewFlagService reviewFlagService;

    @GetMapping("/stats/overview")
    public ResponseEntity<AdminOverviewDTO> getOverview() {
        return ResponseEntity.ok(adminService.getOverview());
    }

    @GetMapping("/stats/analytics")
    public ResponseEntity<AdminAnalyticsDTO> getAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminService.getAnalytics(days));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminDTO>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<UserAdminDTO> createUser(@RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(adminService.createUser(
                request.email(),
                request.password(),
                request.admin()
        ));
    }

    @PatchMapping("/users/{id}/ban")
    public ResponseEntity<UserAdminDTO> banUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.setBanned(id, true));
    }

    @PatchMapping("/users/{id}/unban")
    public ResponseEntity<UserAdminDTO> unbanUser(@PathVariable String id) {
        return ResponseEntity.ok(adminService.setBanned(id, false));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews")
    public ResponseEntity<Page<AdminReviewDTO>> listReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listReviews(page, size));
    }

    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable String id) {
        reviewService.deleteReviewById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/units/recalculate-aggregates")
    public ResponseEntity<Void> recalculateAggregates() {
        unitAggregateService.recalculateAll();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignAdminDTO>> listCampaigns() {
        return ResponseEntity.ok(campaignService.listCampaigns());
    }

    @PostMapping("/campaigns")
    public ResponseEntity<CampaignAdminDTO> createCampaign(@RequestBody CreateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.createCampaign(
                request.slug(),
                request.code(),
                request.name(),
                request.prizeDescription(),
                request.startsAt(),
                request.endsAt(),
                request.maxRedemptions(),
                request.minReviewLength(),
                request.maxEntriesPerUser(),
                request.requireVerifiedStudent(),
                request.requiredReviewCount(),
                request.minLikesReceived(),
                request.minLikesGiven()
        ));
    }

    // Tracking-only referral link: just a slug + optional name, no reward config.
    @PostMapping("/referral-links")
    public ResponseEntity<CampaignAdminDTO> createReferralLink(@RequestBody CreateReferralLinkRequest request) {
        return ResponseEntity.ok(campaignService.createReferralLink(request.slug(), request.name()));
    }

    @PatchMapping("/campaigns/{id}/active")
    public ResponseEntity<CampaignAdminDTO> setCampaignActive(
            @PathVariable String id,
            @RequestBody SetCampaignActiveRequest request) {
        return ResponseEntity.ok(campaignService.setCampaignActive(id, request.active()));
    }

    @GetMapping("/campaigns/{id}/entries")
    public ResponseEntity<List<CampaignEntryAdminDTO>> listCampaignEntries(@PathVariable String id) {
        return ResponseEntity.ok(campaignService.listEntriesForCampaign(id));
    }

    @GetMapping("/unit-requests")
    public ResponseEntity<List<UnitRequestDTO>> listUnitRequests() {
        return ResponseEntity.ok(unitRequestService.getAll().stream().map(UnitRequestService::toDTO).toList());
    }

    @DeleteMapping("/unit-requests/{id}")
    public ResponseEntity<Void> deleteUnitRequest(@PathVariable String id) {
        unitRequestService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/review-flags")
    public ResponseEntity<List<FlaggedReviewDTO>> listFlaggedReviews() {
        return ResponseEntity.ok(reviewFlagService.getFlaggedReviews());
    }

    @DeleteMapping("/review-flags/{reviewId}")
    public ResponseEntity<Void> dismissReviewFlags(@PathVariable String reviewId) {
        reviewFlagService.dismissFlags(reviewId);
        return ResponseEntity.noContent().build();
    }

    public record CreateUserRequest(String email, String password, boolean admin) {}

    public record CreateCampaignRequest(
            String slug,
            String code,
            String name,
            String prizeDescription,
            Instant startsAt,
            Instant endsAt,
            Integer maxRedemptions,
            int minReviewLength,
            int maxEntriesPerUser,
            boolean requireVerifiedStudent,
            int requiredReviewCount,
            int minLikesReceived,
            int minLikesGiven
    ) {}

    public record SetCampaignActiveRequest(boolean active) {}

    public record CreateReferralLinkRequest(String slug, String name) {}
}
