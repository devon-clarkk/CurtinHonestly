package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
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
        // Only allow student emails
        if (!isValidStudentEmail(request.email())) {
            log.warn("Registration failed: Invalid student email {}", request.email());
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"Only student emails are allowed.\"}");
        }

        try {
            User user = userService.createAdminUser(request.email(), request.password());
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );

            log.info("User registered successfully: {}", user.getEmail());
            return ResponseEntity.ok(new JwtResponse(token));
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
        // Only allow student emails
        if (!isValidStudentEmail(request.email())) {
            log.warn("Login failed: Invalid student email {}", request.email());
            return ResponseEntity
                    .status(401)
                    .body("{\"error\": \"Only student emails are allowed.\"}");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            User user = userService.getUserByEmail(request.email());
            String token = jwtUtil.generateToken(
                    user.getEmail(),
                    user.getRoles().stream().map(Enum::name).toList()
            );

            log.info("User logged in successfully: {}", user.getEmail());
            return ResponseEntity.ok(new JwtResponse(token));

        } catch (AuthenticationException ex) {
            log.warn("Login failed for {}: Invalid credentials", request.email());
            return ResponseEntity
                    .status(401)
                    .body("{\"error\": \"Invalid email or password.\"}");
        }
    }

    private boolean isValidStudentEmail(String email) {
        return email.toLowerCase().endsWith("@student.curtin.edu.au");
    }

    // Inner DTO classes
    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record JwtResponse(String token) {}
}