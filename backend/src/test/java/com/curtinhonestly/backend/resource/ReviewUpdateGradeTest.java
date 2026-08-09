package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.dto.ReviewUpdateRequest;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.service.ReviewSecurityService;
import com.curtinhonestly.backend.service.ReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Item 10: editing a review and changing only the final grade threw an "unknown
// error". Two independent defects sit on that path — both are covered here.
@SpringBootTest
@Import(TestcontainersConfig.class)
class ReviewUpdateGradeTest {

    private static final String EMAIL = "grade-edit-test@student.curtin.edu.au";

    @Autowired
    private ReviewService reviewService;
    @Autowired
    private ReviewSecurityService reviewSecurityService;
    @Autowired
    private UnitRepo unitRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Review seedReviewFor(String email, String unitSuffix) {
        Unit unit = new Unit();
        unit.setCode("GRADEEDIT" + unitSuffix);
        unit.setName("Grade Edit Test Unit");
        unit.setDescription("Unit used to reproduce the grade-only edit bug.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        userRepo.saveAndFlush(user);

        ReviewCreateRequest createRequest = new ReviewCreateRequest(
                4, 80, "This is a perfectly ordinary review with enough characters.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, unit.getCode(), null);
        return reviewService.createReview(createRequest);
    }

    // Defect 1: setTags used an immutable Set.of() when tags were absent, which
    // Hibernate's merge cannot clear -> UnsupportedOperationException -> 500.
    @Test
    @WithMockUser(username = EMAIL, authorities = {"ROLE_USER"})
    void editingOnlyTheFinalGradeUpdatesTheReview() {
        Review created = seedReviewFor(EMAIL, String.valueOf(System.nanoTime()));

        // tags null is the case the add-review form's empty tag list resolves to.
        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(
                4, 85, "This is a perfectly ordinary review with enough characters.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, null);
        Review updated = reviewService.updateReview(created.getId(), updateRequest);

        assertThat(updated.getFinalGrade()).isEqualTo(85);
    }

    // Defect 2: the owner check compared the Authentication token (never a
    // UserDetails) with instanceof UserDetails, so every non-admin owner was
    // denied -> the @PreAuthorize on PUT/DELETE /reviews failed for the very user
    // who owns the review. Feed it the same Authentication the SpEL passes.
    private static final String OWNER = "owner-check-test@student.curtin.edu.au";

    @Test
    @WithMockUser(username = OWNER, authorities = {"ROLE_USER"})
    void reviewOwnerIsRecognisedForTheAuthenticatedOwner() {
        Review created = seedReviewFor(OWNER, String.valueOf(System.nanoTime()));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(reviewSecurityService.isReviewOwner(created.getId(), auth)).isTrue();
    }
}
