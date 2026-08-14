package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.UnitTipRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies the tip-ownership check behind @PreAuthorize(IS_ADMIN_OR_TIP_OWNER).
 * The pre-fix instanceof-UserDetails guard denied every non-admin tip owner (the SpEL
 * passes an Authentication, never a UserDetails), so a user could not delete their own
 * tip. These prove the owner is now allowed and a different user is still denied.
 */
@ExtendWith(MockitoExtension.class)
class UnitTipSecurityServiceTest {

    @Mock
    private UnitTipRepo tipRepo;

    @InjectMocks
    private UnitTipSecurityService securityService;

    private Authentication authFor(String email) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of());
    }

    private UnitTip tipOwnedBy(String ownerEmail) {
        User owner = new User();
        owner.setId("user-1");
        owner.setEmail(ownerEmail);
        UnitTip tip = new UnitTip();
        tip.setId("tip-1");
        tip.setUser(owner);
        return tip;
    }

    @Test
    void ownerCanDeleteTheirOwnTip() {
        when(tipRepo.findById("tip-1")).thenReturn(Optional.of(tipOwnedBy("alice@student.curtin.edu.au")));

        assertThat(securityService.isTipOwner("tip-1", authFor("alice@student.curtin.edu.au"))).isTrue();
    }

    @Test
    void differentAuthenticatedUserCannotDeleteSomeoneElsesTip() {
        when(tipRepo.findById("tip-1")).thenReturn(Optional.of(tipOwnedBy("alice@student.curtin.edu.au")));

        assertThat(securityService.isTipOwner("tip-1", authFor("mallory@student.curtin.edu.au"))).isFalse();
    }

    @Test
    void anonymizedTipHasNoOwner() {
        UnitTip anonymized = new UnitTip();
        anonymized.setId("tip-1");
        anonymized.setUser(null);
        when(tipRepo.findById("tip-1")).thenReturn(Optional.of(anonymized));

        assertThat(securityService.isTipOwner("tip-1", authFor("alice@student.curtin.edu.au"))).isFalse();
    }

    @Test
    void unauthenticatedRequestIsDenied() {
        assertThat(securityService.isTipOwner("tip-1", null)).isFalse();
    }

    @Test
    void missingTipIsDenied() {
        when(tipRepo.findById("nope")).thenReturn(Optional.empty());

        assertThat(securityService.isTipOwner("nope", authFor("alice@student.curtin.edu.au"))).isFalse();
    }
}
