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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// A referral link that bundles two campaigns enrols the signup into both, and a
// single qualifying review drops one entry in EACH — Devon's "XYZ = two draws" case.
@SpringBootTest
@Import(TestcontainersConfig.class)
class MultiCampaignEnrolmentTest {

    private static final String EMAIL = "multi-campaign-test@student.curtin.edu.au";

    @Autowired
    private CampaignService campaignService;
    @Autowired
    private ReferralLinkService referralLinkService;
    @Autowired
    private ReviewService reviewService;
    @Autowired
    private UnitRepo unitRepo;

    @Test
    @WithMockUser(username = EMAIL)
    void oneLinkEnrolsInTwoCampaignsAndOneReviewEarnsAnEntryInEach() {
        long ts = System.nanoTime();

        Unit unit = new Unit();
        unit.setCode("MULTI" + ts);
        unit.setName("Multi Campaign Test Unit");
        unit.setDescription("Unit used to prove one review earns an entry in each joined campaign.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);

        Instant start = Instant.now().minusSeconds(60);
        Instant end = Instant.now().plusSeconds(3600);
        // Draw A: one entry for one review. Draw B: one entry per review (cap 10).
        CampaignAdminDTO drawA = campaignService.createCampaign(
                "draw-a-" + ts, "DRAWA" + ts, "Draw A", "Prize A",
                start, end, null, 10, 1, false, 1, 0, 0);
        CampaignAdminDTO drawB = campaignService.createCampaign(
                "draw-b-" + ts, "DRAWB" + ts, "Draw B", "Prize B",
                start, end, null, 10, 10, false, 1, 0, 0);

        // One link that enrols into both draws.
        ReferralLinkAdminDTO link = referralLinkService.createReferralLink(
                "xyz-" + ts, "XYZ", "/", List.of(drawA.getId(), drawB.getId()));
        assertThat(link.campaigns()).hasSize(2);

        // Sign up through the link -> enrolled in BOTH campaigns.
        User user = campaignService.registerUserWithCampaign(EMAIL, "password123", link.slug(), null);
        assertThat(user.getCampaigns()).hasSize(2);

        // One qualifying review -> one entry in each campaign.
        ReviewCreateRequest request = new ReviewCreateRequest(
                4, 80, "This review is long enough to qualify for both draws.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, unit.getCode(), null);
        var result = reviewService.createReviewWithCampaignEntry(request);
        assertThat(result.newEntries()).hasSize(2);

        // Both memberships show on the account view.
        assertThat(campaignService.getCampaignProgress(user)).hasSize(2);
    }
}
