package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        // Only allow student emails
        if (!isValidStudentEmail(request.email())) {
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"Only student emails are allowed.\"}");
        }

        try {
            userService.createAdminUser(request.email(), request.password());
            return ResponseEntity.ok("{\"message\": \"User registered successfully\"}");
        } catch (Exception ex) {
            return ResponseEntity
                    .status(400)
                    .body("{\"error\": \"User registration failed: " + ex.getMessage() + "\"}");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Only allow student emails
        if (!isValidStudentEmail(request.email())) {
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

            return ResponseEntity.ok(new JwtResponse(token));

        } catch (AuthenticationException ex) {
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