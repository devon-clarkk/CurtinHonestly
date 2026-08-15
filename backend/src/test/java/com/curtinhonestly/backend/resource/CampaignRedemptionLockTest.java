package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.repo.CampaignRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Proves the fix-plan #4 lock actually spans the whole redemption: two
// concurrent registrations against a campaign with exactly one slot left
// must not both succeed.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CampaignRedemptionLockTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignRepo campaignRepo;

    @Autowired
    private UserRepo userRepo;

    @Test
    void concurrentRegistrationsCannotExceedMaxRedemptions() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setSlug("lock-test-" + UUID.randomUUID());
        campaign.setCode("LOCK" + System.currentTimeMillis());
        campaign.setName("Redemption Lock Test Campaign");
        campaign.setStartsAt(Instant.now().minusSeconds(60));
        campaign.setEndsAt(Instant.now().plusSeconds(3600));
        campaign.setMaxRedemptions(1);
        campaign.setMinReviewLength(10);
        campaign.setMaxEntriesPerUser(1);
        campaign.setRequireVerifiedStudent(false);
        campaign.setRequiredReviewCount(1);
        campaign.setActive(true);
        campaign = campaignRepo.save(campaign);
        String code = campaign.getCode();

        String emailA = "lock-a-" + UUID.randomUUID() + "@example.com";
        String emailB = "lock-b-" + UUID.randomUUID() + "@example.com";

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> registerA = registerCallable(emailA, code, ready, go);
        Callable<Integer> registerB = registerCallable(emailB, code, ready, go);

        Future<Integer> futureA = executor.submit(registerA);
        Future<Integer> futureB = executor.submit(registerB);

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        int statusA = futureA.get(15, TimeUnit.SECONDS);
        int statusB = futureB.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = List.of(statusA, statusB).stream().filter(s -> s == 200).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(userRepo.countByCampaigns_Id(campaign.getId())).isEqualTo(1);
    }

    private Callable<Integer> registerCallable(String email, String code, CountDownLatch ready, CountDownLatch go) {
        return () -> {
            Map<String, Object> payload = Map.of(
                    "email", email,
                    "password", "password123",
                    "promoCode", code
            );
            String body = objectMapper.writeValueAsString(payload);
            ready.countDown();
            go.await();
            return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }
}
