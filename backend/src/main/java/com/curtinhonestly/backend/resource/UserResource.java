package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CompletedUnitsUpdateRequest;
import com.curtinhonestly.backend.dto.UserAdminDTO;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.AdminService;
import com.curtinhonestly.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserResource {

    private final UserRepo userRepo;
    private final AdminService adminService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<UserAdminDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @GetMapping("/{email}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<User> getUserByEmail(@PathVariable String email) {
        return userRepo.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me/completed-units")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Set<String>> getCompletedUnits() {
        return ResponseEntity.ok(userService.getCompletedUnitCodes(currentUserEmail()));
    }

    @PutMapping("/me/completed-units")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Set<String>> updateCompletedUnits(@Valid @RequestBody CompletedUnitsUpdateRequest request) {
        return ResponseEntity.ok(userService.updateCompletedUnitCodes(currentUserEmail(), request.unitCodes()));
    }

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
