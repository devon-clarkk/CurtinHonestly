package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.StudentEmailValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackOn = Exception.class)
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public User createUser(String email, String password) {
        log.info("Creating user: {}", email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setVerifiedStudent(StudentEmailValidator.isStudentEmail(email));
        user.setRoles(List.of(UserRole.ROLE_USER));

        User savedUser = userRepo.saveAndFlush(user);
        log.info("User created successfully with ID: {}, verifiedStudent={}", savedUser.getId(), savedUser.isVerifiedStudent());
        return savedUser;
    }

    public User getUserByEmail(String email) {
        return userRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User verifyStudentEmail(String email, String studentEmail) {
        User user = getUserByEmail(email);

        if (user.isVerifiedStudent()) {
            throw new IllegalStateException("Account is already verified as a student.");
        }

        if (!StudentEmailValidator.isStudentEmail(studentEmail)) {
            throw new IllegalArgumentException("A valid @student.curtin.edu.au email is required.");
        }

        if (userRepo.findByEmail(studentEmail).filter(existing -> !existing.getId().equals(user.getId())).isPresent()) {
            throw new IllegalArgumentException("That student email is already linked to another account.");
        }

        user.setEmail(studentEmail);
        user.setVerifiedStudent(true);
        User savedUser = userRepo.saveAndFlush(user);
        log.info("User {} verified as student with email {}", savedUser.getId(), studentEmail);
        return savedUser;
    }
}
