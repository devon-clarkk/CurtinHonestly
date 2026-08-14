package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.curtinhonestly.backend.repo.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class EmailNormalizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepo userRepo;

    @Test
    void loginIsCaseInsensitiveAfterMixedCaseRegistration() throws Exception {
        String registeredEmail = "Bob.Normalize@Example.com";
        String password = "password123";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", registeredEmail, "password", password))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "bob.normalize@example.com", "password", password))))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateRegistrationDifferingOnlyByCaseCreatesNoSecondAccount() throws Exception {
        String password = "password123";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "Dup.Case@Example.com", "password", password))))
                .andExpect(status().isOk());

        // The duplicate is still detected across case; what changed is that the caller
        // is no longer told. /auth/register answers identically either way so it can't
        // be used to find out which addresses have accounts (security audit finding #7),
        // which is why this now expects 200 rather than the old 400. The assertion that
        // matters is below: normalization still collapses these two to one account.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "DUP.CASE@EXAMPLE.COM", "password", "a-different-password"))))
                .andExpect(status().isOk());

        assertThat(userRepo.findByEmail("dup.case@example.com")).isPresent();
        // No second row was created under the raw upper-case spelling.
        assertThat(userRepo.findByEmail("DUP.CASE@EXAMPLE.COM")).isEmpty();

        // And the second attempt did not overwrite the real owner's password.
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "dup.case@example.com", "password", password))))
                .andExpect(status().isOk());
    }
}
