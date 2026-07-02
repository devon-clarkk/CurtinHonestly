package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.*;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.AdminService;
import com.curtinhonestly.backend.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminResource {

    private final AdminService adminService;
    private final ReviewService reviewService;

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

    public record CreateUserRequest(String email, String password, boolean admin) {}
}
