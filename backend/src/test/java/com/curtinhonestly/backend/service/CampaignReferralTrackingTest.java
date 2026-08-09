package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignAdminDTO;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// End-to-end coverage for tracking-only referral links: a visit is counted, the
// signup and review that come through the link are attributed to it, and NO draw
// entry is ever created (the reward machinery stays out of the way).
@SpringBootTest
@Import(TestcontainersConfig.class)
class CampaignReferralTrackingTest {

    private static final String EMAIL = "referral-tracking-test@student.curtin.edu.au";

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private CampaignRepo campaignRepo;

    @Autowired
    private UnitRepo unitRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ReviewRepo reviewRepo;

    @Test
    @WithMockUser(username = EMAIL)
    void trackingLinkCountsVisitSignupAndReviewButAwardsNoEntry() {
        String slug = "discord-track-" + System.currentTimeMillis();

        Unit unit = new Unit();
        unit.setCode("REFTRK" + System.currentTimeMillis());
        unit.setName("Referral Tracking Test Unit");
        unit.setDescription("Unit used to prove referral-link attribution counts a review.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);

        // Admin creates a tracking-only link — no prize, no requirement.
        CampaignAdminDTO created = campaignService.createReferralLink(slug, "Computing Discord");
        assertThat(created.isTrackingOnly()).isTrue();
        assertThat(created.getPrizeDescription()).isNull();

        // A visitor opens the link.
        campaignService.recordVisit(slug);
        assertThat(campaignRepo.findBySlugIgnoreCase(slug)).get()
                .extracting(com.curtinhonestly.backend.domain.Campaign::getVisitCount)
                .isEqualTo(1L);

        // The visitor signs up through the link. The signup is attributed via
        // registeredViaRef, and the user is NOT enrolled in the campaign.
        User user = campaignService.registerUserWithCampaign(EMAIL, "password123", slug, null);
        assertThat(user.getCampaign()).isNull();
        assertThat(user.getRegisteredViaRef()).isEqualToIgnoringCase(slug);
        assertThat(userRepo.countByRegisteredViaRefIgnoreCase(slug)).isEqualTo(1);

        // The new user submits a review. No draw entry is created for a tracking link.
        ReviewCreateRequest request = new ReviewCreateRequest(
                4, 80, "This review is long enough to look like a real one.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, unit.getCode(), null);
        var result = reviewService.createReviewWithCampaignEntry(request);
        assertThat(result.campaignEntry()).isEmpty();
        assertThat(reviewRepo.countByUser_RegisteredViaRefIgnoreCase(slug)).isEqualTo(1);

        // The admin view rolls the three counts up per link, with zero entries.
        CampaignAdminDTO dto = campaignService.listCampaigns().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .orElseThrow();
        assertThat(dto.getVisitCount()).isEqualTo(1L);
        assertThat(dto.getSignupCount()).isEqualTo(1L);
        assertThat(dto.getReviewCount()).isEqualTo(1L);
        assertThat(dto.getEntryCount()).isEqualTo(0L);
    }

    @Test
    void referralLinkSlugCannotCollideWithAnExistingPromoCode() {
        // A link slug is resolved as a ?ref= value, sharing a namespace with promo
        // codes, so it must not duplicate an existing campaign's code.
        String sharedCode = "REFDUP" + System.currentTimeMillis();
        campaignService.createCampaign(
                "reward-" + sharedCode, sharedCode, "Reward Campaign", "A prize",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600),
                null, 50, 1, false, 1, 0, 0);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> campaignService.createReferralLink(sharedCode, "Colliding link"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
