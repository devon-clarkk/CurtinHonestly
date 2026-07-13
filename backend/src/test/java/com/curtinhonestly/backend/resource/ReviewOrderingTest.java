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
        Unit unit = new Unit();
        unit.setCode("QFTEST-ORDER");
        unit.setName("Quick Fix Ordering Test Unit");
        unit.setDescription("Unit used to prove reviews come back newest to oldest.");
        unit.setFaculty(Faculty.SCIENCE_AND_ENGINEERING);
        unit.setLevel(UnitLevel.UNDERGRADUATE);
        unitRepo.save(unit);

        User user = new User();
        user.setEmail("ordering-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        userRepo.saveAndFlush(user);
        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        for (String marker : List.of("first-review", "second-review", "third-review")) {
            Map<String, Object> payload = Map.of(
                    "rating", 4,
                    "workload", 5,
                    "reviewText", marker,
                    "unitCode", "QFTEST-ORDER"
            );
            mockMvc.perform(post("/reviews")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payload)))
                    .andExpect(status().isCreated());
            Thread.sleep(5);
        }

        mockMvc.perform(get("/units/QFTEST-ORDER/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reviewText").value("third-review"))
                .andExpect(jsonPath("$[1].reviewText").value("second-review"))
                .andExpect(jsonPath("$[2].reviewText").value("first-review"));
    }
}
