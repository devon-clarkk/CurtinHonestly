package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.repo.UnitTipRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UnitTipSecurityService {

    private final UnitTipRepo tipRepo;

    public boolean isTipOwner(String tipId, Object principal) {
        try {
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                UnitTip tip = tipRepo.findById(tipId).orElse(null);
                // Anonymized tips (author deleted their account) have no owner.
                return tip != null && tip.getUser() != null && tip.getUser().getEmail().equals(username);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
