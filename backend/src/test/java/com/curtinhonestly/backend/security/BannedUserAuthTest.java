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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class BannedUserAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void bannedUserWithValidTokenIsRejected() throws Exception {
        User user = new User();
        user.setEmail("banned-user-test@student.curtin.edu.au");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(List.of(UserRole.ROLE_USER));
        user.setBanned(false);
        user = userRepo.saveAndFlush(user);

        String token = jwtUtil.generateToken(user.getEmail(), List.of("ROLE_USER"));

        // Sanity check: a valid, non-banned user can access a protected endpoint.
        mockMvc.perform(get("/reviews/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        user.setBanned(true);
        userRepo.saveAndFlush(user);

        // The token is still cryptographically valid and unexpired, but the user
        // is now banned - the filter must reject it rather than waiting for expiry.
        mockMvc.perform(get("/reviews/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
