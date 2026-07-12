package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.repo.CampaignRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CampaignValidateTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignRepo campaignRepo;

    @Test
    void notFoundExpiredAndFullReturnTheSameBody() throws Exception {
        Campaign expired = new Campaign();
        expired.setSlug("expired-" + UUID.randomUUID());
        expired.setCode("EXP" + System.currentTimeMillis());
        expired.setName("Expired Campaign");
        expired.setStartsAt(Instant.now().minusSeconds(7200));
        expired.setEndsAt(Instant.now().minusSeconds(3600));
        expired.setActive(true);
        expired = campaignRepo.save(expired);

        Campaign full = new Campaign();
        full.setSlug("full-" + UUID.randomUUID());
        full.setCode("FULL" + System.currentTimeMillis());
        full.setName("Full Campaign");
        full.setStartsAt(Instant.now().minusSeconds(60));
        full.setEndsAt(Instant.now().plusSeconds(3600));
        full.setMaxRedemptions(0); // zero slots -> always reads as full
        full.setActive(true);
        full = campaignRepo.save(full);

        MvcResult notFoundResult = mockMvc.perform(get("/campaigns/validate").param("code", "DOES-NOT-EXIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andReturn();

        MvcResult expiredResult = mockMvc.perform(get("/campaigns/validate").param("code", expired.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andReturn();

        MvcResult fullResult = mockMvc.perform(get("/campaigns/validate").param("code", full.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andReturn();

        String notFoundBody = notFoundResult.getResponse().getContentAsString();
        String expiredBody = expiredResult.getResponse().getContentAsString();
        String fullBody = fullResult.getResponse().getContentAsString();

        // Same message and no campaign details leaked for any invalid reason -
        // an attacker probing codes can't tell not-found from expired from full.
        assertThat(expiredBody).isEqualTo(notFoundBody);
        assertThat(fullBody).isEqualTo(notFoundBody);
    }

    @Test
    void exceedingTheRateLimitReturns429() throws Exception {
        int lastStatus = 0;
        for (int i = 0; i < 11; i++) {
            lastStatus = mockMvc.perform(get("/campaigns/validate").param("code", "RATE-LIMIT-PROBE-" + i))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        }
        assertThat(lastStatus).isEqualTo(429);
    }
}
