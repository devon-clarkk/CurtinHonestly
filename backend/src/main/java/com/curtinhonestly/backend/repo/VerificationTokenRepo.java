package com.curtinhonestly.backend.repo;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.VerificationPurpose;
import com.curtinhonestly.backend.domain.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VerificationTokenRepo extends JpaRepository<VerificationToken, String> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /**
     * Invalidate any outstanding unused tokens for a user + purpose, so issuing a
     * fresh link supersedes older ones.
     */
    @Modifying
    @Query("update VerificationToken t set t.usedAt = CURRENT_TIMESTAMP " +
            "where t.user = :user and t.purpose = :purpose and t.usedAt is null")
    void invalidateOutstanding(User user, VerificationPurpose purpose);
}
