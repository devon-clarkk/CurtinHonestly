package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Unit;
import com.curtinhonestly.backend.domain.UnitTip;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.repo.UnitRepo;
import com.curtinhonestly.backend.repo.UnitTipRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnitTipServiceTest {

    @Mock UnitTipRepo tipRepo;
    @Mock UnitRepo unitRepo;
    @Mock UserRepo userRepo;
    @Mock ProfanityFilterService profanityFilterService;

    @Captor ArgumentCaptor<UnitTip> tipCaptor;

    private UnitTipService service() {
        return new UnitTipService(tipRepo, unitRepo, userRepo, profanityFilterService);
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.setContext(new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken(email, "pw", List.of())));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Unit unit() {
        Unit unit = new Unit();
        unit.setId("unit-1");
        unit.setCode("ISYS1000");
        return unit;
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("alice@student.curtin.edu.au");
        return user;
    }

    @Test
    void createTip_savesTrimmedTextAgainstTheUnitAndCurrentUser() {
        authenticateAs("alice@student.curtin.edu.au");
        when(unitRepo.findByCode("ISYS1000")).thenReturn(Optional.of(unit()));
        when(userRepo.findByEmail("alice@student.curtin.edu.au")).thenReturn(Optional.of(user()));
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(tipRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UnitTip result = service().createTip("ISYS1000", "  The mid-sem test is harder than expected.  ");

        verify(tipRepo).save(tipCaptor.capture());
        UnitTip saved = tipCaptor.getValue();
        assertThat(saved.getText()).isEqualTo("The mid-sem test is harder than expected.");
        assertThat(saved.getUnit().getCode()).isEqualTo("ISYS1000");
        assertThat(saved.getUser().getId()).isEqualTo("user-1");
        assertThat(result.getText()).isEqualTo("The mid-sem test is harder than expected.");
    }

    @Test
    void createTip_rejectsBlankText() {
        assertThatThrownBy(() -> service().createTip("ISYS1000", "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tipRepo, unitRepo);
    }

    @Test
    void createTip_rejectsTextOverTwoHundredChars() {
        String tooLong = "x".repeat(201);
        assertThatThrownBy(() -> service().createTip("ISYS1000", tooLong))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tipRepo, unitRepo);
    }

    @Test
    void createTip_rejectsProfanity() {
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service().createTip("ISYS1000", "some bad text"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tipRepo, unitRepo);
    }

    @Test
    void createTip_rejectsUnknownUnit() {
        when(profanityFilterService.containsProfanity(anyString())).thenReturn(false);
        when(unitRepo.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().createTip("NOPE", "A fine tip."))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tipRepo, never()).save(any());
    }

    @Test
    void getTipsForUnit_returnsNewestFirstFromRepo() {
        when(unitRepo.findByCode("ISYS1000")).thenReturn(Optional.of(unit()));
        UnitTip tip = new UnitTip();
        when(tipRepo.findByUnit_IdOrderByCreatedAtDesc("unit-1")).thenReturn(List.of(tip));

        List<UnitTip> result = service().getTipsForUnit("ISYS1000");

        assertThat(result).containsExactly(tip);
    }

    @Test
    void deleteTip_delegatesToRepo() {
        service().deleteTip("tip-1");
        verify(tipRepo).deleteById("tip-1");
    }
}
