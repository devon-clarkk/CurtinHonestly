package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AccountDTO;
import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.dto.CampaignEntrySummaryDTO;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.CampaignService;
import com.curtinhonestly.backend.service.EmailAlreadyRegisteredException;
import com.curtinhonestly.backend.service.UserService;
import com.curtinhonestly.backend.service.VerificationService;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // Byte-for-byte identical whether or not the address already had an account, so
    // /auth/register cannot be used to enumerate who has signed up (security audit
    // finding #7). Registering no longer returns a session token, because a token can only
    // be minted for an account we created, and returning one in exactly one of the
    // two branches is the leak. The client completes signup by calling /auth/login,
    // which is already enumeration-safe.
    private static final String REGISTER_RESPONSE_MESSAGE =
            "Thanks for signing up. If that email wasn't already registered, your account is ready. Sign in to continue.";

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt received");

        try {
            User user = campaignService.registerUserWithCampaign(
                    request.email(), request.password(), request.ref(), request.promoCode());

            // If they registered with a student-suffix address, send a confirmation link to it.
            // The badge stays off until they click it.
            if (!user.isVerifiedStudent() && StudentEmailValidator.isStudentEmail(user.getEmail())) {
                verificationService.requestStudentVerification(user, user.getEmail());
            }

            log.info("User registered successfully: {}", user.getId());
        } catch (EmailAlreadyRegisteredException ex) {
            // Deliberately swallowed. The owner of the address is notified by email
            // (UserService.createUser); the caller gets the same 200 as a new signup.
            // Note the email itself is never logged here: the log line would be the
            // same oracle, just moved into the log file.
            log.info("Registration attempt for an address that already has an account; returning the uniform response");
        }

        return ResponseEntity.ok(new MessageResponse(REGISTER_RESPONSE_MESSAGE));
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
        return ResponseEntity.ok(accountFor(userService.getUserByEmail(email)));
    }

    // Join a campaign after signup by entering its referral link, campaign slug, or
    // promo code on the account page. Returns the refreshed account so the new
    // campaign(s) and any credited entries appear immediately.
    @PostMapping("/me/campaigns")
    public ResponseEntity<AccountDTO> enrolInCampaign(@RequestBody EnrolCampaignRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        campaignService.enrolCurrentUserByCode(email, request.code());
        return ResponseEntity.ok(accountFor(userService.getUserByEmail(email)));
    }

    private AccountDTO accountFor(User user) {
        return new AccountDTO(
                user.getEmail(),
                user.isVerifiedStudent(),
                campaignService.getCampaignProgress(user),
                campaignService.getEntriesForUser(user)
        );
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

    // POST with the token in the body, not GET with it in the query string
    // (security audit finding #5). This call both consumes a single-use token and
    // mints a session, and a URL-borne token leaks through Referer headers, browser
    // history, and every proxy/access log on the way. The emailed link still lands
    // on the SPA route /verify-student/confirm?token=..., which is unavoidable for a
    // link in an email; the SPA strips the token from its own URL and hands it to
    // this endpoint in a request body.
    @PostMapping("/verify-student/confirm")
    public ResponseEntity<?> confirmStudent(@Valid @RequestBody ConfirmStudentRequest request) {
        User user = verificationService.confirmStudentVerification(request.token());
        String jwt = jwtUtil.generateToken(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).toList()
        );
        log.info("Student verification confirmed for user {}", user.getId());
        return ResponseEntity.ok()
                // Belt and braces: this response body carries a session token, so make
                // sure no shared cache or bfcache-style store keeps a copy of it.
                .header("Cache-Control", "no-store")
                .body(new JwtResponse(jwt, user.isVerifiedStudent()));
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

    // Password floor matches VerificationService.resetPassword (min 8) so the
    // strength policy is consistent between signup and reset.
    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8, max = 200) String password, String ref, String promoCode) {}
    public record LoginRequest(String email, String password) {}
    public record VerifyStudentRequest(String studentEmail, String password) {}
    public record ConfirmStudentRequest(@NotBlank String token) {}
    public record UpdateEmailRequest(String newEmail, String password) {}
    public record EnrolCampaignRequest(String code) {}
    public record DeleteAccountRequest(String password, boolean deleteReviews) {}
    public record ForgotPasswordRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}
    public record JwtResponse(String token, boolean verifiedStudent) {}
    public record MessageResponse(String message) {}
}
