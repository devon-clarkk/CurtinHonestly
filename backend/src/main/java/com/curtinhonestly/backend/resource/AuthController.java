package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.AccountDTO;
import com.curtinhonestly.backend.security.JwtUtil;
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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

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
            User user = userService.createUser(request.email(), request.password());
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
        return ResponseEntity.ok(new AccountDTO(user.isVerifiedStudent()));
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

    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record VerifyStudentRequest(String studentEmail, String password) {}
    public record JwtResponse(String token, boolean verifiedStudent) {}
}
