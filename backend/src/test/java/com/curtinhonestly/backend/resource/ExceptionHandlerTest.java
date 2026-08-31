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
        // An unresolvable referral slug throws IllegalArgumentException out of
        // CampaignService, which must come back as a 400 carrying that fixed safe
        // message - never a raw DB or stack detail.
        //
        // This used to assert on a duplicate registration instead. That path no longer
        // 400s: /auth/register now answers identically whether or not the address is
        // taken (security audit finding #7), which is asserted below and in
        // RegisterEnumerationTest. Only the duplicate-email case is swallowed, so an
        // unrelated failure like this one is still the right probe for the handler.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "bad-ref-exc-test@student.curtin.edu.au",
                                "password", "password123",
                                "ref", "no-such-campaign-slug"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Campaign not found. Check your referral link or promo code.")));
    }

    @Test
    void duplicateRegistrationIsIndistinguishableFromANewOne() throws Exception {
        String password = "password123";

        String freshBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "dup-enum-test@student.curtin.edu.au", "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Same address a second time. Security audit finding #7: the status and the
        // body must match the first call exactly, or /auth/register is an oracle for
        // which addresses have accounts. End-to-end counterpart to the unit-level
        // assertions in RegisterEnumerationTest.
        String duplicateBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "dup-enum-test@student.curtin.edu.au", "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(duplicateBody).isEqualTo(freshBody);
        // No error envelope in either case. (Not a substring check on the message text:
        // the uniform message legitimately reads "if that email wasn't already
        // registered", so searching for that phrase would fail on the correct output.)
        org.assertj.core.api.Assertions.assertThat(duplicateBody).doesNotContain("\"error\"");
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
                "rating", 99, // out of the 1-5 range
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
