package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitTipRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class UnitTipService {

    private static final int MAX_TIP_LENGTH = 200;

    private final UnitTipRepo tipRepo;
    private final UnitRepo unitRepo;
    private final UserRepo userRepo;
    private final ProfanityFilterService profanityFilterService;

    public UnitTip createTip(String unitCode, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Tip text is required.");
        }
        if (text.length() > MAX_TIP_LENGTH) {
            throw new IllegalArgumentException("Tips must be " + MAX_TIP_LENGTH + " characters or fewer.");
        }
        if (profanityFilterService.containsProfanity(text)) {
            throw new IllegalArgumentException("Your tip contains language that violates our community standards.");
        }

        Unit unit = unitRepo.findByCode(unitCode)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitCode));
        User user = currentUser();

        UnitTip tip = new UnitTip();
        tip.setUnit(unit);
        tip.setUser(user);
        tip.setText(text);

        UnitTip saved = tipRepo.save(tip);
        log.info("User {} added a tip to unit {}", user.getId(), unitCode);
        return saved;
    }

    public List<UnitTip> getTipsForUnit(String unitCode) {
        Unit unit = unitRepo.findByCode(unitCode)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + unitCode));
        return tipRepo.findByUnit_IdOrderByCreatedAtDesc(unit.getId());
    }

    public void deleteTip(String tipId) {
        tipRepo.deleteById(tipId);
    }

    public Optional<String> currentUserIdIfAuthenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(name).map(User::getId);
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
    }
}
