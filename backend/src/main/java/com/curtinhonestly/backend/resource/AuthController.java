package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.Campaign;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AccountDTO;
import com.curtinhonestly.backend.dto.CampaignEntrySummaryDTO;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.CampaignService;
import com.curtinhonestly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final CampaignService campaignService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());
        if (!isValidEmail(request.email())) {
            log.warn("Registration failed: Invalid email format {}", request.email());
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"Please provide a valid email address.\"}");
        }

        try {
            Optional<Campaign> campaignOpt = hasCampaignAttribution(request)
                    ? campaignService.resolveCampaignForRegistration(request.ref(), request.promoCode())
                    : Optional.empty();

            if (hasCampaignAttribution(request) && campaignOpt.isEmpty()) {
                return ResponseEntity.status(400)
                        .body("{\"error\": \"Campaign not found. Check your referral link or promo code.\"}");
            }

            if (campaignOpt.isPresent()) {
                var validation = campaignService.validateCampaign(request.ref(), request.promoCode());
                if (!validation.isValid()) {
                    return ResponseEntity.status(400)
                            .body("{\"error\": \"" + validation.getMessage() + "\"}");
                }
            }

            User user = userService.createUser(
                    request.email(),
                    request.password(),
                    campaignOpt.orElse(null),
                    request.ref()
            );
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );

            log.info("User registered successfully: {}", user.getId());
            return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
        } catch (Exception ex) {
            log.error("User registration failed for {}: {}", request.email(), ex.getMessage());
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"User registration failed: " + ex.getMessage() + "\"}");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            User user = userService.getUserByEmail(request.email());
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );

            log.info("User logged in successfully: {}", user.getId());
            return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));

        } catch (AuthenticationException ex) {
            log.warn("Login failed for {}: Invalid credentials", request.email());
            return ResponseEntity
                    .status(401)
                    .body("{\"error\": \"Invalid email or password.\"}");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<AccountDTO> getCurrentAccount() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.getUserByEmail(email);
        var campaign = user.getCampaign();
        List<CampaignEntrySummaryDTO> entries = campaignService.getEntriesForUser(user);

        return ResponseEntity.ok(new AccountDTO(
                user.getEmail(),
                user.isVerifiedStudent(),
                campaign != null ? campaign.getName() : null,
                campaign != null ? campaign.getPrizeDescription() : null,
                campaign != null ? campaign.getEndsAt() : null,
                campaignService.getCampaignProgress(user),
                entries
        ));
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateEmail(@RequestBody UpdateEmailRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            User user = userService.updateEmail(email, request.newEmail(), request.password());
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );
            return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body("{\"error\": \"" + ex.getMessage() + "\"}");
        }
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(@RequestBody DeleteAccountRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            userService.deleteAccount(email, request.password());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(400).body("{\"error\": \"" + ex.getMessage() + "\"}");
        }
    }

    @PostMapping("/verify-student")
    public ResponseEntity<?> verifyStudent(@RequestBody VerifyStudentRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException ex) {
            return ResponseEntity
                    .status(401)
                    .body("{\"error\": \"Invalid password.\"}");
        }

        try {
            User user = userService.verifyStudentEmail(email, request.studentEmail());
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );
            return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"" + ex.getMessage() + "\"}");
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    private boolean hasCampaignAttribution(RegisterRequest request) {
        return (request.ref() != null && !request.ref().isBlank())
                || (request.promoCode() != null && !request.promoCode().isBlank());
    }

    public record RegisterRequest(String email, String password, String ref, String promoCode) {}
    public record LoginRequest(String email, String password) {}
    public record VerifyStudentRequest(String studentEmail, String password) {}
    public record UpdateEmailRequest(String newEmail, String password) {}
    public record DeleteAccountRequest(String password) {}
    public record JwtResponse(String token, boolean verifiedStudent) {}
}
