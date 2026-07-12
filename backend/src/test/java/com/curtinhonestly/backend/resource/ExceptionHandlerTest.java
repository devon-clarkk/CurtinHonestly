package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void illegalArgumentExceptionMapsTo400WithSafeMessage() throws Exception {
        String email = "duplicate-exc-test@student.curtin.edu.au";
        String password = "password123";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk());

        // Second registration with the same email hits UserService's duplicate check
        // (IllegalArgumentException), which must come back as a 400 with a fixed safe
        // message - never a raw DB constraint error.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("That email is already registered.")));
    }

    @Test
    void beanValidationFailureMapsTo400() throws Exception {
        User user = new User();
        user.setEmail("validation-exc-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        Map<String, Object> invalidPayload = Map.of(
                "rating", 99, // out of the 0-5 range
                "workload", 5,
                "unitCode", "SOME-CODE"
        );

        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unhandledExceptionMapsTo500WithGenericMessage() throws Exception {
        User user = new User();
        user.setEmail("unhandled-exc-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        Map<String, Object> payload = Map.of(
                "rating", 4,
                "workload", 5,
                "unitCode", "UNIT-CODE-THAT-DOES-NOT-EXIST"
        );

        // Unit lookup fails with a plain RuntimeException, not IllegalArgumentException -
        // must be caught by the generic handler and never leak the "Unit not found" detail.
        mockMvc.perform(post("/reviews")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("An unexpected error occurred. Please try again later.")))
                .andExpect(jsonPath("$.error", not(containsString("UNIT-CODE-THAT-DOES-NOT-EXIST"))));
    }
}
