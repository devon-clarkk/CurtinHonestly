package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.AcademicTerm;
import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Review;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.ReviewCreateRequest;
import com.curtinhonestly.backend.repo.CampaignEntryRepo;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.ReviewRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ReviewLikeCampaignTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnitRepo unitRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private CampaignRepo campaignRepo;

    @Autowired
    private CampaignEntryRepo campaignEntryRepo;

    @Autowired
    private ReviewRepo reviewRepo;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @WithMockUser(username = "like-author@student.curtin.edu.au")
    void likingAReviewIncrementsAggregateAndCanUnlockCampaignEntry() throws Exception {
        Unit authorUnit = saveUnit("LIKEAUTH");
        Unit otherUnit = saveUnit("LIKEOTHER");

        Campaign campaign = new Campaign();
        campaign.setSlug("like-camp-" + UUID.randomUUID());
        campaign.setCode("LIKE" + System.currentTimeMillis());
        campaign.setName("Likes Campaign");
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setMinReviewLength(10);
        campaign.setMaxEntriesPerUser(5);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setMinLikesReceived(1);
        campaign.setMinLikesGiven(0);
        campaign.setActive(true);
        campaign = campaignRepo.save(campaign);

        User author = saveUser("like-author@student.curtin.edu.au", campaign);
        User liker = saveUser("like-liker@student.curtin.edu.au", null);

        ReviewCreateRequest request = new ReviewCreateRequest(
                4, 80, "This review is long enough to qualify once it receives a like.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Test", 5, true, true, authorUnit.getCode(), null);

        var created = reviewService.createReviewWithCampaignEntry(request);
        assertThat(created.newEntries()).isEmpty();
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), author.getId())).isZero();

        Review review = reviewRepo.findById(created.review().getId()).orElseThrow();
        assertThat(review.getLikeCount()).isZero();

        String likerToken = jwtUtil.generateToken(liker.getEmail(), List.of("ROLE_USER"));

        mockMvc.perform(post("/reviews/" + review.getId() + "/likes")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.likedByCurrentUser").value(true));

        Review refreshed = reviewRepo.findById(review.getId()).orElseThrow();
        assertThat(refreshed.getLikeCount()).isEqualTo(1);
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), author.getId())).isEqualTo(1);

        mockMvc.perform(get("/units/" + authorUnit.getCode())
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].id").value(review.getId()))
                .andExpect(jsonPath("$.reviews[0].likeCount").value(1))
                .andExpect(jsonPath("$.reviews[0].likedByCurrentUser").value(true));

        mockMvc.perform(delete("/reviews/" + review.getId() + "/likes")
                        .header("Authorization", "Bearer " + likerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByCurrentUser").value(false));

        // Entries are not revoked when likes drop.
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), author.getId())).isEqualTo(1);

        // Seed a second unit solely so we have another review target if needed later.
        assertThat(unitRepo.findByCode(otherUnit.getCode())).isPresent();
    }

    @Test
    @WithMockUser(username = "likes-given-author@student.curtin.edu.au")
    void campaignRequiresLikesGivenBeforeAwardingEntry() throws Exception {
        Unit authorUnit = saveUnit("LIKEGIVEN");
        Unit targetUnit = saveUnit("LIKETARGET");

        Campaign campaign = new Campaign();
        campaign.setSlug("likes-given-" + UUID.randomUUID());
        campaign.setCode("GIVEN" + System.currentTimeMillis());
        campaign.setName("Must Like Something");
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setMinReviewLength(10);
        campaign.setMaxEntriesPerUser(5);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setMinLikesReceived(0);
        campaign.setMinLikesGiven(1);
        campaign.setActive(true);
        campaign = campaignRepo.save(campaign);

        User author = saveUser("likes-given-author@student.curtin.edu.au", campaign);
        User other = saveUser("likes-given-other@student.curtin.edu.au", null);

        // Other user's review to like.
        Review target = new Review();
        target.setRating(4);
        target.setReviewText("Someone else's helpful review text.");
        target.setWorkload(5);
        target.setHasExam(false);
        target.setWouldTakeAgain(true);
        target.setUnit(targetUnit);
        target.setUser(other);
        target.setCreatedAt(Instant.now());
        target = reviewRepo.save(target);

        ReviewCreateRequest request = new ReviewCreateRequest(
                5, 85, "Author review that qualifies on length but waits on the likes-given gate.",
                AcademicTerm.SEMESTER_1, 2026, "Prof Gate", 4, false, true, authorUnit.getCode(), null);

        var created = reviewService.createReviewWithCampaignEntry(request);
        assertThat(created.newEntries()).isEmpty();
        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), author.getId())).isZero();

        String authorToken = jwtUtil.generateToken(author.getEmail(), List.of("ROLE_USER"));
        mockMvc.perform(post("/reviews/" + target.getId() + "/likes")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));

        assertThat(campaignEntryRepo.countByCampaign_IdAndUser_Id(campaign.getId(), author.getId())).isEqualTo(1);
    }

    @Test
    void cannotLikeOwnReview() throws Exception {
        Unit unit = saveUnit("OWNLIKE");
        User user = saveUser("own-like@student.curtin.edu.au", null);

        Review review = new Review();
        review.setRating(3);
        review.setReviewText("My own review.");
        review.setWorkload(5);
        review.setHasExam(true);
        review.setWouldTakeAgain(false);
        review.setUnit(unit);
        review.setUser(user);
        review.setCreatedAt(Instant.now());
        review = reviewRepo.save(review);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));
        mockMvc.perform(post("/reviews/" + review.getId() + "/likes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private Unit saveUnit(String prefix) {
        Unit unit = new Unit();
        unit.setCode(prefix + System.currentTimeMillis());
        unit.setName(prefix + " Unit");
        unit.setDescription("Unit for like tests.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        return unitRepo.save(unit);
    }

    private User saveUser(String email, Campaign campaign) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user.getCampaigns().add(campaign);
        return userRepo.saveAndFlush(user);
    }
}
