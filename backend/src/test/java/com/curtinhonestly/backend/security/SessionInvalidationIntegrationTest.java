package com.curtinhonestly.backend.security;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security audit finding #4, end to end against a real Postgres: the
 * {@code tokens_valid_after} column persists through JPA and Flyway, and the JWT
 * filter actually turns it into a 401 on a protected endpoint.
 *
 * <p>{@link JwtSessionInvalidationTest} covers the comparison logic in isolation and
 * {@code VerificationServiceTest} covers the stamping; this covers the wiring between
 * them, which is where a nullable column added by migration would silently fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SessionInvalidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void aCredentialChangeCutOffRevokesAnAlreadyIssuedToken() throws Exception {
        User user = new User();
        user.setEmail("session-invalidation-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        // Pre-existing accounts have a null cut-off and must keep working: the column
        // must not log out a userbase that has never reset anything.
        assertThat(user.getTokensValidAfter()).isNull();
        mockMvc.perform(get("/reviews/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // What VerificationService.resetPassword does on a real reset. Stamped a second
        // into the future so the already-issued token is unambiguously older, without
        // depending on how long the assertions above took.
        user.setTokensValidAfter(Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.SECONDS));
        userRepo.saveAndFlush(user);

        // Same token: still correctly signed, still unexpired, now refused. This is the
        // whole point of the finding - before it, a reset left a stolen token usable for
        // the remainder of its 7-day TTL.
        mockMvc.perform(get("/reviews/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // And the value survived the round trip to Postgres rather than being dropped by
        // a column the migration never created.
        assertThat(userRepo.findByEmail(user.getEmail()).orElseThrow().getTokensValidAfter()).isNotNull();
    }

    @Test
    void aTokenIssuedAfterTheCutOffKeepsWorking() throws Exception {
        User user = new User();
        user.setEmail("session-invalidation-fresh@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user.setTokensValidAfter(Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS));
        user = userRepo.saveAndFlush(user);

        // Signing in again after a reset has to work, or the fix is a lockout.
        String freshToken = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        mockMvc.perform(get("/reviews/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());
    }
}
