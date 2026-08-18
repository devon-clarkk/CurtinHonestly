package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.Faculty;
import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitLevel;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ReviewOrderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnitRepo unitRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void unitReviewsAreReturnedNewestFirst() throws Exception {
        String code = "QFTEST-ORDER";
        createUnit(code);
        postReviews(code, "first-review", "second-review", "third-review");

        mockMvc.perform(get("/units/" + code + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewText").value("third-review"))
                .andExpect(jsonPath("$[1].reviewText").value("second-review"))
                .andExpect(jsonPath("$[2].reviewText").value("first-review"));

        // The endpoint the unit page actually calls. This assertion is the one
        // that was missing: /units/{code}/reviews was ordered all along, while
        // /units/{code} handed back the raw JPA collection in heap order.
        mockMvc.perform(get("/units/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].reviewText").value("third-review"))
                .andExpect(jsonPath("$.reviews[1].reviewText").value("second-review"))
                .andExpect(jsonPath("$.reviews[2].reviewText").value("first-review"));
    }

    @Test
    void aLikedReviewOutranksAMarginallyNewerOne() throws Exception {
        String code = "QFTEST-ORDER-LIKES";
        createUnit(code);
        postReviews(code, "liked-review", "newer-review");

        String likedId = reviewIdWithText(code, "liked-review");
        mockMvc.perform(post("/reviews/" + likedId + "/likes")
                        .header("Authorization", "Bearer " + tokenFor("liker@student.curtin.edu.au")))
                .andExpect(status().isOk());

        // These two are milliseconds apart, so a single like is worth far more
        // than the age gap and flips the order. ReviewRankingTest covers where
        // the crossover actually sits.
        mockMvc.perform(get("/units/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews[0].reviewText").value("liked-review"))
                .andExpect(jsonPath("$.reviews[1].reviewText").value("newer-review"));
    }

    private void createUnit(String code) {
        Unit unit = new Unit();
        unit.setCode(code);
        unit.setName("Quick Fix Ordering Test Unit " + code);
        unit.setDescription("Unit used to prove reviews come back in ranked order.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unitRepo.save(unit);
    }

    /**
     * One review per author, in the order given: a user may only review a unit
     * once, so posting several reviews to one unit needs several users.
     */
    private void postReviews(String unitCode, String... markers) throws Exception {
        for (String marker : markers) {
            String token = tokenFor(marker + "-" + unitCode.toLowerCase() + "@student.curtin.edu.au");
            Map<String, Object> payload = Map.of(
                    "rating", 4,
                    "workload", 5,
                    "reviewText", marker,
                    "unitCode", unitCode
            );
            mockMvc.perform(post("/reviews")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());
            Thread.sleep(5);
        }
    }

    private String tokenFor(String email) {
        User user = userRepo.findByEmail(email).orElseGet(() -> {
            User created = new User();
            created.setEmail(email);
            created.setPassword(passwordEncoder.encode("password123"));
            created.setRoles(List.of(UserRole.ROLE_USER));
            return userRepo.saveAndFlush(created);
        });
        return jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));
    }

    private String reviewIdWithText(String unitCode, String reviewText) throws Exception {
        String body = mockMvc.perform(get("/units/" + unitCode + "/reviews"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        for (JsonNode review : objectMapper.readTree(body)) {
            if (reviewText.equals(review.path("reviewText").asText())) {
                return review.path("id").asText();
            }
        }
        throw new AssertionError("No review found with text: " + reviewText);
    }
}
