package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CampaignAdminDTO;
import com.curtinhonestly.backend.dto.ReferralLinkAdminDTO;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A signed-up user can enrol themselves later by entering a code on their account
// page — including a multi-campaign referral link — and reviews they already left
// are credited retroactively.
@SpringBootTest
@Import(TestcontainersConfig.class)
class EnrolByCodeTest {

    private static final String EMAIL = "enrol-by-code-test@student.curtin.edu.au";

    @Autowired
    private CampaignService campaignService;
    @Autowired
    private ReferralLinkService referralLinkService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private UnitRepo unitRepo;
    @Autowired
    private UserRepo userRepo;

    @Test
    @WithMockUser(username = EMAIL)
    void enrollingByLinkCodeJoinsAllDrawsAndCreditsExistingReviews() {
        long ts = System.nanoTime();

        Unit unit = new Unit();
        unit.setCode("ENROL" + ts);
        unit.setName("Enrol By Code Test Unit");
        unit.setDescription("Unit used to prove existing reviews are credited on late enrolment.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);

        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now().plusSeconds(3600);
        CampaignAdminDTO drawA = campaignService.createCampaign(
                "enrol-a-" + ts, "ENA" + ts, "Draw A", "Prize A", start, end, null, 10, 5, false, 1, 0, 0);
        CampaignAdminDTO drawB = campaignService.createCampaign(
                "enrol-b-" + ts, "ENB" + ts, "Draw B", "Prize B", start, end, null, 10, 5, false, 1, 0, 0);
        ReferralLinkAdminDTO link = referralLinkService.createReferralLink(
                "enrol-link-" + ts, "XYZ", "/", List.of(drawA.getId(), drawB.getId()));

        // A user who signed up WITHOUT any campaign, and already left a review.
        User user = campaignService.registerUserWithCampaign(EMAIL, "password123", null, null);
        assertThat(user.getCampaigns()).isEmpty();
        reviewService.createReviewWithCampaignEntry(new ReviewCreateRequest(
                4, 80, "A review left before joining any campaign, long enough to qualify.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, unit.getCode(), null));

        // They enter the link code on their account page -> joined both draws, and the
        // review they already left is credited in each.
        campaignService.enrolCurrentUserByCode(EMAIL, link.slug());

        // Enrolled in both draws (checked via repo counts — the lazy campaigns
        // collection can't initialize outside a transaction in the test), and the
        // review they already left is credited once in each.
        assertThat(userRepo.countByCampaigns_Id(drawA.getId())).isEqualTo(1);
        assertThat(userRepo.countByCampaigns_Id(drawB.getId())).isEqualTo(1);
        User reloaded = userRepo.findByEmail(EMAIL).orElseThrow();
        assertThat(campaignService.getEntriesForUser(reloaded)).hasSize(2);

        // Entering the same code again is rejected (already in).
        assertThatThrownBy(() -> campaignService.enrolCurrentUserByCode(EMAIL, link.slug()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
