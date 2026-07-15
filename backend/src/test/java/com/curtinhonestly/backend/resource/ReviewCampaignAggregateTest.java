package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Confirms that wiring the campaign entry-awarding hook onto the
// ReviewCreateRequest DTO path (reconciled with dev's DTO-based POST /reviews)
// did not drop the perf-work aggregate recalculation dev added separately.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ReviewCampaignAggregateTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void submittingAReviewWithCampaignAttributionStillRecalculatesUnitAggregates() throws Exception {
        Unit unit = new Unit();
        unit.setCode("AGGTEST" + System.currentTimeMillis());
        unit.setName("Aggregate Test Unit");
        unit.setDescription("Unit used to prove aggregates still recalculate under the campaign path.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unit = unitRepo.save(unit);
        assertThat(unit.getReviewCount()).isZero();

        Campaign campaign = new Campaign();
        campaign.setSlug("agg-test-" + UUID.randomUUID());
        campaign.setCode("AGGTEST" + System.currentTimeMillis());
        campaign.setName("Aggregate Test Campaign");
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setMinReviewLength(10);
        campaign.setMaxEntriesPerUser(5);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setActive(true);
        campaign = campaignRepo.save(campaign);

        User user = new User();
        user.setEmail("agg-campaign-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user.setCampaign(campaign);
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        Map<String, Object> payload = Map.of(
                "rating", 5,
                "finalGrade", 90,
                "reviewText", "This review is long enough to qualify for a campaign entry.",
                "semesterTaken", "Semester 1, 2026",
                "professor", "Prof Test",
                "workload", 5,
                "hasExam", true,
                "wouldTakeAgain", true,
                "unitCode", unit.getCode()
        );

        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());

        Unit refreshed = unitRepo.findById(unit.getId()).orElseThrow();
        assertThat(refreshed.getReviewCount()).isEqualTo(1);
        assertThat(refreshed.getAverageRating()).isEqualTo(5.0);
    }
}
