package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AccountDTO;
import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.UserService;
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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for email: {}", request.email());

        User user = userService.createUser(request.email(), request.password());
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
        return ResponseEntity.ok(new AccountDTO(user.getEmail(), user.isVerifiedStudent()));
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

        userService.deleteAccount(email, request.password());
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

        User user = userService.verifyStudentEmail(email, request.studentEmail());
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).toList()
        );
        return ResponseEntity.ok(new JwtResponse(token, user.isVerifiedStudent()));
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record LoginRequest(String email, String password) {}
    public record VerifyStudentRequest(String studentEmail, String password) {}
    public record UpdateEmailRequest(String newEmail, String password) {}
    public record DeleteAccountRequest(String password) {}
    public record JwtResponse(String token, boolean verifiedStudent) {}
}
