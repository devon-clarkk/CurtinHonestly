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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ReviewCreateDtoTest {

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
    void clientSuppliedIdAndCreatedAtAreIgnored() throws Exception {
        Unit unit = new Unit();
        unit.setCode("DTOTEST101");
        unit.setName("DTO Test Unit");
        unit.setDescription("Unit used to prove createdAt/id are server-assigned.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unitRepo.save(unit);

        User user = new User();
        user.setEmail("dto-review-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        Map<String, Object> maliciousPayload = new LinkedHashMap<>();
        maliciousPayload.put("id", "client-supplied-id-should-be-ignored");
        maliciousPayload.put("createdAt", "2000-01-01T00:00:00Z");
        maliciousPayload.put("rating", 4);
        maliciousPayload.put("finalGrade", 80);
        maliciousPayload.put("reviewText", "Solid unit.");
        maliciousPayload.put("workload", 5);
        maliciousPayload.put("hasExam", true);
        maliciousPayload.put("wouldTakeAgain", true);
        maliciousPayload.put("unitCode", "DTOTEST101");

        MvcResult result = mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousPayload)))
                .andExpect(status().isCreated())
                .andReturn();

        // POST /reviews now returns a CreateReviewResponseDTO envelope (review +
        // campaign entry fields) rather than the bare ReviewDTO, since the campaign
        // work wraps review creation to award entries in the same call.
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Instant returnedCreatedAt = Instant.parse(body.get("review").get("createdAt").asText());

        assertThat(returnedCreatedAt).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
        assertThat(result.getResponse().getHeader("Location")).doesNotContain("client-supplied-id-should-be-ignored");
    }
}
