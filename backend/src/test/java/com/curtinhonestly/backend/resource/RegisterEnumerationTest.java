package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.security.JwtUtil;
import com.curtinhonestly.backend.service.CampaignService;
import com.curtinhonestly.backend.service.EmailAlreadyRegisteredException;
import com.curtinhonestly.backend.service.UserService;
import com.curtinhonestly.backend.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security audit finding #7 — {@code POST /auth/register} must not tell an
 * unauthenticated caller which email addresses already have accounts.
 *
 * <p>Previously a duplicate came back as {@code 400 "That email is already
 * registered."} while a new address came back {@code 200}, so the endpoint was a
 * free account-enumeration oracle for any address someone cared to try.
 *
 * <p>The response now cannot depend on which branch ran, which is also why
 * registration stopped returning a session token: a token can only be minted for
 * an account we just created, so returning one in exactly one of the two cases
 * would be the tell all over again. The SPA signs in immediately afterwards.
 */
@ExtendWith(MockitoExtension.class)
class RegisterEnumerationTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtUtil jwtUtil;
    @Mock UserService userService;
    @Mock CampaignService campaignService;
    @Mock VerificationService verificationService;

    private AuthController controller() {
        return new AuthController(authenticationManager, jwtUtil, userService, campaignService, verificationService);
    }

    private static AuthController.RegisterRequest request(String email) {
        return new AuthController.RegisterRequest(email, "password123", null, null);
    }

    private static User newUser(String email) {
        User user = new User();
        user.setId("user-1");
        user.setEmail(email);
        user.setRoles(List.of(UserRole.ROLE_USER));
        return user;
    }

    @Test
    void aBrandNewEmailAndAnAlreadyRegisteredOneGetByteIdenticalResponses() {
        when(campaignService.registerUserWithCampaign(any(), any(), any(), any()))
                .thenReturn(newUser("fresh@gmail.com"))
                .thenThrow(new EmailAlreadyRegisteredException("That email is already registered."));

        AuthController controller = controller();
        ResponseEntity<?> forNewEmail = controller.register(request("fresh@gmail.com"));
        ResponseEntity<?> forTakenEmail = controller.register(request("taken@gmail.com"));

        assertThat(forNewEmail.getStatusCode()).isEqualTo(forTakenEmail.getStatusCode());
        assertThat(forNewEmail.getStatusCode().value()).isEqualTo(200);
        // Same body instance content, not merely the same shape — anything that varies
        // between the branches (a message, a flag, a null field) is the leak.
        assertThat(forNewEmail.getBody()).isEqualTo(forTakenEmail.getBody());
        assertThat(forNewEmail.getHeaders()).isEqualTo(forTakenEmail.getHeaders());
    }

    @Test
    void registeringNeverReturnsASessionToken() {
        when(campaignService.registerUserWithCampaign(any(), any(), any(), any()))
                .thenReturn(newUser("fresh@gmail.com"));

        ResponseEntity<?> response = controller().register(request("fresh@gmail.com"));

        // A token in the success branch only would reintroduce the oracle, since the
        // duplicate branch has no account to mint one for.
        assertThat(response.getBody()).isInstanceOf(AuthController.MessageResponse.class);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void aDuplicateSignupTriggersNoVerificationEmailToTheCaller() {
        when(campaignService.registerUserWithCampaign(any(), any(), any(), any()))
                .thenThrow(new EmailAlreadyRegisteredException("That email is already registered."));

        controller().register(request("taken@student.curtin.edu.au"));

        // The student-suffix branch must not run for an address we did not create an
        // account for: a confirmation link landing in that inbox is itself a signal,
        // and it would be a link for someone else's account. (UserService separately
        // sends the owner a plain "someone tried to sign up" notice.)
        verify(verificationService, never()).requestStudentVerification(any(), anyString());
    }

    @Test
    void aNewStudentEmailStillGetsItsConfirmationLink() {
        when(campaignService.registerUserWithCampaign(any(), any(), any(), any()))
                .thenReturn(newUser("fresh@student.curtin.edu.au"));

        controller().register(request("fresh@student.curtin.edu.au"));

        // The uniform response must not have quietly cost real signups their
        // verification email.
        verify(verificationService).requestStudentVerification(any(), anyString());
    }

    @Test
    void unrelatedRegistrationFailuresStillSurfaceAsErrors() {
        // Only the one duplicate-email case is swallowed. A bad referral link or promo
        // code is not an enumeration signal about email addresses, and silently
        // pretending it worked would hide a real mistake from the user.
        when(campaignService.registerUserWithCampaign(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Campaign not found. Check your referral link or promo code."));

        assertThatThrownBy(() -> controller().register(request("fresh@gmail.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(EmailAlreadyRegisteredException.class);
    }
}
