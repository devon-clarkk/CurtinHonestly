package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.repo.UnitTipRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnitTipSecurityService {

    private final UnitTipRepo tipRepo;

    // The @PreAuthorize SpEL passes `authentication` (the Authentication token
    // itself), which is never a UserDetails — the old `instanceof UserDetails`
    // check therefore always failed, denying every non-admin tip owner. Read the
    // username off getName() the same way ReviewSecurityService does.
    public boolean isTipOwner(String tipId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        try {
            String username = authentication.getName();
            UnitTip tip = tipRepo.findById(tipId).orElse(null);
            // Anonymized tips (author deleted their account) have no owner.
            return tip != null && tip.getUser() != null && tip.getUser().getEmail().equals(username);
        } catch (Exception e) {
            return false;
        }
    }
}
