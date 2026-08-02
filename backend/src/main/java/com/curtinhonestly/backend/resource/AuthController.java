package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AccountDTO;
import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.dto.CampaignEntrySummaryDTO;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.CampaignService;
import com.curtinhonestly.backend.service.UserService;
import com.curtinhonestly.backend.service.VerificationService;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final CampaignService campaignService;
    private final VerificationService verificationService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());

        User user = campaignService.registerUserWithCampaign(
                request.email(), request.password(), request.ref(), request.promoCode());

        // If they registered with a student-suffix address, send a confirmation link to it.
        // The badge stays off until they click it.
        if (!user.isVerifiedStudent() && StudentEmailValidator.isStudentEmail(user.getEmail())) {
            verificationService.requestStudentVerification(user, user.getEmail());
        }

        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).toList()
        );

        log.info("User registered successfully: {}", user.getId());
        return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
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
                    .body(new ErrorResponse("Invalid email or password."));
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

        User user = userService.updateEmail(email, request.newEmail(), request.password());
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).toList()
        );
        return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteAccount(@RequestBody DeleteAccountRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        userService.deleteAccount(email, request.password(), request.deleteReviews());
        return ResponseEntity.noContent().build();
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
                    .body(new ErrorResponse("Invalid password."));
        }

        User user = userService.getUserByEmail(email);
        verificationService.requestStudentVerification(user, request.studentEmail());
        return ResponseEntity.ok(new MessageResponse(
                "We've sent a confirmation link to your student email. Click it to verify your account."));
    }

    @GetMapping("/verify-student/confirm")
    public ResponseEntity<?> confirmStudent(@RequestParam("token") String token) {
        User user = verificationService.confirmStudentVerification(token);
        String jwt = jwtUtil.generateToken(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).toList()
        );
        log.info("Student verification confirmed for user {}", user.getId());
        return ResponseEntity.ok(new JwtResponse(jwt, user.isVerifiedStudent()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Enumeration-safe: always return the same response whether or not the email exists.
        verificationService.requestPasswordReset(request.email());
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that email, we've sent a password reset link."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        verificationService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Your password has been reset. You can now log in with your new password."));
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String password, String ref, String promoCode) {}
    public record LoginRequest(String email, String password) {}
    public record VerifyStudentRequest(String studentEmail, String password) {}
    public record UpdateEmailRequest(String newEmail, String password) {}
    public record DeleteAccountRequest(String password, boolean deleteReviews) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record JwtResponse(String token, boolean verifiedStudent) {}
    public record MessageResponse(String message) {}
}
