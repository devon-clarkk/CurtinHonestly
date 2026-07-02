package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.*;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class AdminService {

    private final UserRepo userRepo;
    private final ReviewRepo reviewRepo;
    private final UnitRepo unitRepo;
    private final UserService userService;

    public AdminOverviewDTO getOverview() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Review> reviews = reviewRepo.findAll();

        double avgRating = reviews.stream()
                .mapToInt(Review::getRating)
                .average()
                .orElse(0);

        return new AdminOverviewDTO(
                userRepo.count(),
                reviewRepo.count(),
                unitRepo.count(),
                reviewRepo.countByCreatedAtAfter(sevenDaysAgo),
                userRepo.countByCreatedAtAfter(sevenDaysAgo),
                Math.round(avgRating * 10.0) / 10.0
        );
    }

    public AdminAnalyticsDTO getAnalytics(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<User> recentUsers = userRepo.findByCreatedAtAfter(since);
        List<Review> recentReviews = reviewRepo.findByCreatedAtAfter(since);
        List<Review> allReviews = reviewRepo.findAll();

        Map<String, Long> reviewsByFaculty = allReviews.stream()
                .filter(r -> r.getUnit() != null && r.getUnit().getFaculty() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getUnit().getFaculty().name(),
                        Collectors.counting()
                ));

        double avgWorkload = allReviews.stream()
                .mapToInt(Review::getWorkload)
                .average()
                .orElse(0);

        long wouldTakeAgain = allReviews.stream().filter(Review::isWouldTakeAgain).count();
        double wouldTakeAgainRatio = allReviews.isEmpty() ? 0 : (double) wouldTakeAgain / allReviews.size();

        return new AdminAnalyticsDTO(
                buildTimeSeries(recentUsers, recentReviews, days),
                reviewsByFaculty,
                Math.round(avgWorkload * 10.0) / 10.0,
                Math.round(wouldTakeAgainRatio * 100.0) / 100.0
        );
    }

    public List<UserAdminDTO> listUsers() {
        return userRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::toUserAdminDTO)
                .toList();
    }

    public Page<AdminReviewDTO> listReviews(int page, int size) {
        return reviewRepo.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toAdminReviewDTO);
    }

    public UserAdminDTO createUser(String email, String password, boolean admin) {
        User user = admin
                ? userService.createAdminUser(email, password)
                : userService.createUser(email, password);
        return toUserAdminDTO(user);
    }

    public UserAdminDTO setBanned(String userId, boolean banned) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBanned(banned);
        return toUserAdminDTO(userRepo.save(user));
    }

    public void deleteUser(String userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        userRepo.delete(user);
    }

    private UserAdminDTO toUserAdminDTO(User user) {
        long reviewCount = user.getReviews() != null ? user.getReviews().size() : 0;
        List<String> roles = user.getRoles().stream().map(Enum::name).toList();
        return new UserAdminDTO(
                user.getId(),
                user.getEmail(),
                user.isVerifiedStudent(),
                roles,
                user.isBanned(),
                reviewCount,
                user.getCreatedAt()
        );
    }

    private AdminReviewDTO toAdminReviewDTO(Review review) {
        String unitCode = review.getUnit() != null ? review.getUnit().getCode() : "unknown";
        String authorEmail = review.getUser() != null ? review.getUser().getEmail() : "unknown";
        return new AdminReviewDTO(
                review.getId(),
                unitCode,
                authorEmail,
                review.getRating(),
                review.getReviewText(),
                review.getCreatedAt()
        );
    }

    private List<TimeSeriesPointDTO> buildTimeSeries(List<User> users, List<Review> reviews, int days) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        Map<LocalDate, long[]> buckets = new TreeMap<>();

        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        for (int i = 0; i < days; i++) {
            buckets.put(start.plusDays(i), new long[]{0, 0});
        }

        for (User user : users) {
            if (user.getCreatedAt() == null) continue;
            LocalDate day = user.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (buckets.containsKey(day)) {
                buckets.get(day)[0]++;
            }
        }

        for (Review review : reviews) {
            if (review.getCreatedAt() == null) continue;
            LocalDate day = review.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (buckets.containsKey(day)) {
                buckets.get(day)[1]++;
            }
        }

        return buckets.entrySet().stream()
                .map(e -> new TimeSeriesPointDTO(
                        e.getKey().format(formatter),
                        e.getValue()[0],
                        e.getValue()[1]
                ))
                .toList();
    }
}
