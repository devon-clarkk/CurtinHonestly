package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.repo.CampaignEntryRepo;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
class CampaignReEarnTest {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private CampaignRepo campaignRepo;

    @Autowired
    private CampaignEntryRepo campaignEntryRepo;

    @Autowired
    private UnitRepo unitRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(username = "re-earn-test@student.curtin.edu.au")
    void deletingAndResubmittingAReviewDoesNotEarnASecondEntry() {
        Unit unit = new Unit();
        unit.setCode("REEARN" + System.currentTimeMillis());
        unit.setName("Re-earn Test Unit");
        unit.setDescription("Unit used to prove the delete-and-resubmit loop is closed.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);

        Campaign campaign = new Campaign();
        campaign.setSlug("re-earn-" + UUID.randomUUID());
        campaign.setCode("REEARN" + System.currentTimeMillis());
        campaign.setName("Re-earn Test Campaign");
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setMinReviewLength(10);
        campaign.setMaxEntriesPerUser(5);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setActive(true);
        campaign = campaignRepo.save(campaign);

        User user = new User();
        user.setEmail("re-earn-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user.setCampaign(campaign);
        user = userRepo.saveAndFlush(user);

        ReviewCreateRequest request = new ReviewCreateRequest(
                4, 80, "This review is long enough to qualify for the campaign entry.",
                "Semester 1, 2026", "Prof Test", 5, true, true, unit.getCode(), null);

        // First submission earns an entry.
        var firstResult = reviewService.createReviewWithCampaignEntry(request);
        assertThat(firstResult.campaignEntry()).isPresent();
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId())).isEqualTo(1);

        // Delete the review - the entry (and its token) must survive.
        reviewService.deleteReviewById(firstResult.review().getId());
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId())).isEqualTo(1);

        // Resubmitting a fresh qualifying review for the SAME unit must not earn a second entry.
        var secondResult = reviewService.createReviewWithCampaignEntry(request);
        assertThat(secondResult.campaignEntry()).isEmpty();
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), user.getId())).isEqualTo(1);
    }
}
