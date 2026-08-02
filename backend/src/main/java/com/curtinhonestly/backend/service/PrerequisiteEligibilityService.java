package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitPrerequisiteGroupDTO;
import com.curtinhonestly.backend.dto.UnitPrerequisiteOptionDTO;
import com.curtinhonestly.backend.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates a unit's prerequisite groups against the current user's
 * self-reported completed units (roadmap 4.4). Only unit-level options
 * (UnitPrerequisiteOptionDTO) can be checked this way — course-level
 * requirements (CoursePrerequisiteOptionDTO, e.g. "24 credits in
 * Engineering") aren't tracked, so a group that depends on one is marked
 * unverifiable rather than guessed at.
 */
@Service
@RequiredArgsConstructor
public class PrerequisiteEligibilityService {

    private final UserRepo userRepo;

    public void enrichWithEligibility(UnitDetailsDTO details) {
        List<UnitPrerequisiteGroupDTO> groups = details.getPrerequisiteGroups();
        if (groups == null || groups.isEmpty()) {
            return;
        }

        Optional<User> currentUser = currentUser();
        if (currentUser.isEmpty()) {
            return;
        }

        Set<String> completed = currentUser.get().getCompletedUnitCodes() == null
                ? Set.of()
                : currentUser.get().getCompletedUnitCodes().stream()
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());

        for (UnitPrerequisiteGroupDTO group : groups) {
            GroupEligibility eligibility = evaluateGroup(group, completed);
            group.setSatisfied(eligibility.satisfied());
            group.setUnverifiable(eligibility.unverifiable());
        }

        details.setPrerequisitesEligible(overallEligibility(groups));
    }

    private GroupEligibility evaluateGroup(UnitPrerequisiteGroupDTO group, Set<String> completed) {
        List<UnitPrerequisiteOptionDTO> options = group.getOptions() != null ? group.getOptions() : List.of();
        boolean hasCourseOptions = group.getCourseOptions() != null && !group.getCourseOptions().isEmpty();
        boolean isAll = "all".equalsIgnoreCase(group.getRequirement());

        if (isAll) {
            boolean allUnitOptionsMet = options.stream().allMatch(o -> isCompleted(o, completed));
            if (!allUnitOptionsMet) {
                // A required unit option is missing — no unverifiable course
                // requirement can rescue this, the group is definitively unmet.
                return new GroupEligibility(false, false);
            }
            return hasCourseOptions ? new GroupEligibility(null, true) : new GroupEligibility(true, false);
        }

        // "select one" (or any non-"all" value)
        boolean anyUnitOptionMet = options.stream().anyMatch(o -> isCompleted(o, completed));
        if (anyUnitOptionMet) {
            return new GroupEligibility(true, false);
        }
        if (hasCourseOptions || options.isEmpty()) {
            // Either an unchecked course option might satisfy it, or there's
            // nothing checkable in this group at all.
            return new GroupEligibility(null, true);
        }
        return new GroupEligibility(false, false);
    }

    private boolean isCompleted(UnitPrerequisiteOptionDTO option, Set<String> completed) {
        return option.getCode() != null && completed.contains(option.getCode().toUpperCase());
    }

    private Boolean overallEligibility(List<UnitPrerequisiteGroupDTO> groups) {
        boolean anyFalse = groups.stream().anyMatch(g -> Boolean.FALSE.equals(g.getSatisfied()));
        if (anyFalse) {
            return false;
        }
        boolean anyUnverifiable = groups.stream().anyMatch(UnitPrerequisiteGroupDTO::isUnverifiable);
        if (anyUnverifiable) {
            return null;
        }
        return true;
    }

    private Optional<User> currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return userRepo.findByEmail(name);
    }

    private record GroupEligibility(Boolean satisfied, boolean unverifiable) {}
}
