package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserResource {

    private final UserRepo userRepo;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepo.findAll());
    }

    @GetMapping("/{email}")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userRepo.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
