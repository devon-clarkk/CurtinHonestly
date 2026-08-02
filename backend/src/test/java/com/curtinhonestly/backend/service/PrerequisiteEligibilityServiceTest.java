package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.dto.CoursePrerequisiteOptionDTO;
import com.curtinhonestly.backend.dto.UnitDetailsDTO;
import com.curtinhonestly.backend.dto.UnitPrerequisiteGroupDTO;
import com.curtinhonestly.backend.dto.UnitPrerequisiteOptionDTO;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrerequisiteEligibilityServiceTest {

    @Mock UserRepo userRepo;

    private PrerequisiteEligibilityService service() {
        return new PrerequisiteEligibilityService(userRepo);
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, "pw", List.of())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User userWithCompleted(String... codes) {
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@student.curtin.edu.au");
        user.setCompletedUnitCodes(Set.of(codes));
        return user;
    }

    private UnitPrerequisiteOptionDTO option(String code) {
        UnitPrerequisiteOptionDTO dto = new UnitPrerequisiteOptionDTO();
        dto.setCode(code);
        return dto;
    }

    private UnitPrerequisiteGroupDTO group(String requirement, List<UnitPrerequisiteOptionDTO> options,
                                            List<CoursePrerequisiteOptionDTO> courseOptions) {
        UnitPrerequisiteGroupDTO dto = new UnitPrerequisiteGroupDTO();
        dto.setRequirement(requirement);
        dto.setOptions(options);
        dto.setCourseOptions(courseOptions);
        return dto;
    }

    private UnitDetailsDTO detailsWithGroups(UnitPrerequisiteGroupDTO... groups) {
        UnitDetailsDTO details = new UnitDetailsDTO();
        details.setPrerequisiteGroups(List.of(groups));
        return details;
    }

    @Test
    void anonymousRequest_leavesGroupsUnchanged() {
        UnitDetailsDTO details = detailsWithGroups(group("all", List.of(option("COMP1000")), List.of()));

        service().enrichWithEligibility(details);

        assertThat(details.getPrerequisiteGroups().get(0).getSatisfied()).isNull();
        assertThat(details.getPrerequisitesEligible()).isNull();
    }

    @Test
    void requireAll_satisfiedWhenEveryOptionCompleted() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted("COMP1000", "COMP1001")));
        UnitDetailsDTO details = detailsWithGroups(
                group("all", List.of(option("COMP1000"), option("comp1001")), List.of()));

        service().enrichWithEligibility(details);

        UnitPrerequisiteGroupDTO result = details.getPrerequisiteGroups().get(0);
        assertThat(result.getSatisfied()).isTrue();
        assertThat(result.isUnverifiable()).isFalse();
        assertThat(details.getPrerequisitesEligible()).isTrue();
    }

    @Test
    void requireAll_notSatisfiedWhenOneOptionMissing_evenWithUncheckableCourseOption() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted("COMP1000")));
        UnitDetailsDTO details = detailsWithGroups(
                group("all", List.of(option("COMP1000"), option("COMP1001")), List.of(new CoursePrerequisiteOptionDTO())));

        service().enrichWithEligibility(details);

        UnitPrerequisiteGroupDTO result = details.getPrerequisiteGroups().get(0);
        assertThat(result.getSatisfied()).isFalse();
        assertThat(result.isUnverifiable()).isFalse();
        assertThat(details.getPrerequisitesEligible()).isFalse();
    }

    @Test
    void requireOne_satisfiedWhenAnyOptionCompleted() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted("COMP1001")));
        UnitDetailsDTO details = detailsWithGroups(
                group("one", List.of(option("COMP1000"), option("COMP1001")), List.of()));

        service().enrichWithEligibility(details);

        assertThat(details.getPrerequisiteGroups().get(0).getSatisfied()).isTrue();
        assertThat(details.getPrerequisitesEligible()).isTrue();
    }

    @Test
    void requireOne_unverifiableWhenNoUnitOptionMetButCourseOptionExists() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted()));
        UnitDetailsDTO details = detailsWithGroups(
                group("one", List.of(option("COMP1000")), List.of(new CoursePrerequisiteOptionDTO())));

        service().enrichWithEligibility(details);

        UnitPrerequisiteGroupDTO result = details.getPrerequisiteGroups().get(0);
        assertThat(result.getSatisfied()).isNull();
        assertThat(result.isUnverifiable()).isTrue();
        assertThat(details.getPrerequisitesEligible()).isNull();
    }

    @Test
    void requireOne_notSatisfiedWhenNoOptionsMetAndNoCourseOptions() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted()));
        UnitDetailsDTO details = detailsWithGroups(
                group("one", List.of(option("COMP1000")), List.of()));

        service().enrichWithEligibility(details);

        assertThat(details.getPrerequisiteGroups().get(0).getSatisfied()).isFalse();
        assertThat(details.getPrerequisitesEligible()).isFalse();
    }

    @Test
    void overallEligibility_falseWinsOverUnverifiableAcrossMultipleGroups() {
        authenticateAs("alice@student.curtin.edu.au");
        when(userRepo.findByEmail("alice@student.curtin.edu.au"))
                .thenReturn(Optional.of(userWithCompleted()));
        UnitDetailsDTO details = detailsWithGroups(
                group("one", List.of(option("COMP1000")), List.of(new CoursePrerequisiteOptionDTO())),
                group("all", List.of(option("COMP1001")), List.of()));

        service().enrichWithEligibility(details);

        assertThat(details.getPrerequisitesEligible()).isFalse();
    }

    @Test
    void noGroups_leavesOverallEligibilityNull() {
        authenticateAs("alice@student.curtin.edu.au");
        UnitDetailsDTO details = new UnitDetailsDTO();
        details.setPrerequisiteGroups(List.of());

        service().enrichWithEligibility(details);

        assertThat(details.getPrerequisitesEligible()).isNull();
    }
}
