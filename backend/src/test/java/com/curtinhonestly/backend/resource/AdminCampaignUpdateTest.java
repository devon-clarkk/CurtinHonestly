package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers PATCH /admin/campaigns/{id}: the edit path that lets an admin move a
// campaign's end date from the panel instead of running SQL against prod.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AdminCampaignUpdateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepo campaignRepo;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Instant START = Instant.parse("2026-08-01T02:00:00Z");
    private static final Instant ORIGINAL_END = Instant.parse("2026-09-01T15:59:00Z");
    private static final Instant EXTENDED_END = Instant.parse("2026-09-05T15:59:00Z");

    private Campaign seedCampaign() {
        Campaign campaign = new Campaign();
        campaign.setSlug("edit-test-" + UUID.randomUUID());
        campaign.setCode("EDIT" + System.nanoTime());
        campaign.setName("Past Students Draw");
        campaign.setPrizeDescription("50 dollar gift card");
        campaign.setStartsAt(START);
        campaign.setEndsAt(ORIGINAL_END);
        campaign.setActive(true);
        campaign.setMaxRedemptions(100);
        campaign.setMinReviewLength(50);
        campaign.setMaxEntriesPerUser(5);
        campaign.setRequireVerifiedStudent(true);
        campaign.setRequiredReviewCount(1);
        campaign.setMinLikesReceived(0);
        campaign.setMinLikesGiven(0);
        return campaignRepo.save(campaign);
    }

    // The full editable set, prefilled from the row the way the admin form does.
    private Map<String, Object> editableBodyFor(Campaign campaign) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", campaign.getName());
        body.put("prizeDescription", campaign.getPrizeDescription());
        body.put("startsAt", campaign.getStartsAt().toString());
        body.put("endsAt", campaign.getEndsAt().toString());
        body.put("maxRedemptions", campaign.getMaxRedemptions());
        body.put("minReviewLength", campaign.getMinReviewLength());
        body.put("maxEntriesPerUser", campaign.getMaxEntriesPerUser());
        body.put("requireVerifiedStudent", campaign.isRequireVerifiedStudent());
        body.put("requiredReviewCount", campaign.getRequiredReviewCount());
        body.put("minLikesReceived", campaign.getMinLikesReceived());
        body.put("minLikesGiven", campaign.getMinLikesGiven());
        body.put("landingPath", campaign.getLandingPath());
        return body;
    }

    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void adminMovesTheEndDateAndNothingElseChanges() throws Exception {
        Campaign campaign = seedCampaign();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("endsAt", EXTENDED_END.toString());

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endsAt").exists());

        Campaign reloaded = campaignRepo.findById(campaign.getId()).orElseThrow();

        // The date moved by four days and carried its time-of-day along unchanged.
        assertThat(reloaded.getEndsAt()).isEqualTo(EXTENDED_END);

        // Everything else is exactly what it was.
        assertThat(reloaded.getStartsAt()).isEqualTo(START);
        assertThat(reloaded.getSlug()).isEqualTo(campaign.getSlug());
        assertThat(reloaded.getCode()).isEqualTo(campaign.getCode());
        assertThat(reloaded.getName()).isEqualTo("Past Students Draw");
        assertThat(reloaded.getPrizeDescription()).isEqualTo("50 dollar gift card");
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getMaxRedemptions()).isEqualTo(100);
        assertThat(reloaded.getMinReviewLength()).isEqualTo(50);
        assertThat(reloaded.getMaxEntriesPerUser()).isEqualTo(5);
        assertThat(reloaded.isRequireVerifiedStudent()).isTrue();
        assertThat(reloaded.getRequiredReviewCount()).isEqualTo(1);
        assertThat(reloaded.isTrackingOnly()).isFalse();
        assertThat(reloaded.getVisitCount()).isEqualTo(campaign.getVisitCount());
        // Truncated: Instant.now() carries nanoseconds, Postgres stores microseconds,
        // so the pre-save object and the reloaded row differ below the microsecond.
        assertThat(reloaded.getCreatedAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(campaign.getCreatedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void otherEditableFieldsAlsoSave() throws Exception {
        Campaign campaign = seedCampaign();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("name", "  Renamed Draw  ");
        body.put("prizeDescription", "100 dollar gift card");
        body.put("maxRedemptions", null);
        body.put("minLikesReceived", 3);

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Campaign reloaded = campaignRepo.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed Draw");
        assertThat(reloaded.getPrizeDescription()).isEqualTo("100 dollar gift card");
        // A full-replace body is what makes clearing the cap expressible at all.
        assertThat(reloaded.getMaxRedemptions()).isNull();
        assertThat(reloaded.getMinLikesReceived()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void endDateBeforeStartDateIsRejected() throws Exception {
        Campaign campaign = seedCampaign();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("endsAt", START.minusSeconds(3600).toString());

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Campaign end date must be after the start date."));

        // The rejected write left the row untouched.
        assertThat(campaignRepo.findById(campaign.getId()).orElseThrow().getEndsAt())
                .isEqualTo(ORIGINAL_END);
    }

    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void blankNameIsRejected() throws Exception {
        Campaign campaign = seedCampaign();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("name", "   ");

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Campaign name is required."));
    }

    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void unknownCampaignIdIsRejected() throws Exception {
        Campaign campaign = seedCampaign();

        mockMvc.perform(patch("/admin/campaigns/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editableBodyFor(campaign))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Campaign not found."));
    }

    // Identity and mode are not fields of the request record, so Jackson drops them.
    // Sending them is a no-op rather than a way to rewrite a live share link.
    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void immutableFieldsInTheBodyAreIgnored() throws Exception {
        Campaign campaign = seedCampaign();
        String originalSlug = campaign.getSlug();
        String originalCode = campaign.getCode();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("slug", "hijacked-slug");
        body.put("code", "HIJACKED");
        body.put("trackingOnly", true);
        body.put("visitCount", 9999);
        body.put("id", UUID.randomUUID().toString());

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Campaign reloaded = campaignRepo.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.getSlug()).isEqualTo(originalSlug);
        assertThat(reloaded.getCode()).isEqualTo(originalCode);
        assertThat(reloaded.isTrackingOnly()).isFalse();
        assertThat(reloaded.getVisitCount()).isEqualTo(0L);
    }

    // active is owned solely by PATCH /admin/campaigns/{id}/active. An edit saved from
    // a form prefilled before someone deactivated the campaign must not revive it.
    @Test
    @WithMockUser(username = "admin@curtinhonestly.com", authorities = {"ROLE_ADMIN"})
    void editingDoesNotReactivateADeactivatedCampaign() throws Exception {
        Campaign campaign = seedCampaign();
        campaign.setActive(false);
        campaign = campaignRepo.save(campaign);

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("endsAt", EXTENDED_END.toString());
        body.put("active", true);

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Campaign reloaded = campaignRepo.findById(campaign.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getEndsAt()).isEqualTo(EXTENDED_END);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        Campaign campaign = seedCampaign();

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editableBodyFor(campaign))))
                .andExpect(status().isUnauthorized());

        assertThat(campaignRepo.findById(campaign.getId()).orElseThrow().getEndsAt())
                .isEqualTo(ORIGINAL_END);
    }

    @Test
    @WithMockUser(username = "student@student.curtin.edu.au", authorities = {"ROLE_USER"})
    void nonAdminRequestIsForbidden() throws Exception {
        Campaign campaign = seedCampaign();

        Map<String, Object> body = editableBodyFor(campaign);
        body.put("endsAt", EXTENDED_END.toString());

        mockMvc.perform(patch("/admin/campaigns/" + campaign.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        assertThat(campaignRepo.findById(campaign.getId()).orElseThrow().getEndsAt())
                .isEqualTo(ORIGINAL_END);
    }
}
