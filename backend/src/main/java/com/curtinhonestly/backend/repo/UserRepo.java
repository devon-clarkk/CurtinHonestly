package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepo extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    long countByCreatedAtAfter(Instant since);
    List<User> findByCreatedAtAfter(Instant since);
}
