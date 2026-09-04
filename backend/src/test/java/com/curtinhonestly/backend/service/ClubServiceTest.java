package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Club;
import com.curtinhonestly.backend.domain.ClubMember;
import com.curtinhonestly.backend.domain.ClubMemberRole;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.AdminClubDTO;
import com.curtinhonestly.backend.dto.AdminClubMemberRequest;
import com.curtinhonestly.backend.dto.AdminClubUpsertRequest;
import com.curtinhonestly.backend.dto.ClubPortalClubDTO;
import com.curtinhonestly.backend.dto.ClubProfileUpdateRequest;
import com.curtinhonestly.backend.repo.ClubEventRepo;
import com.curtinhonestly.backend.repo.ClubMemberRepo;
import com.curtinhonestly.backend.repo.ClubRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Membership grants and revokes ROLE_CLUB; the portal access checks decide
 * who may do what for a club. Both run through Mockito repos.
 */
@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock ClubRepo clubRepo;
    @Mock ClubMemberRepo memberRepo;
    @Mock ClubEventRepo eventRepo;
    @Mock UserRepo userRepo;
    @Mock ProfanityFilterService profanityFilterService;

    private ClubService service() {
        return new ClubService(clubRepo, memberRepo, eventRepo, userRepo, profanityFilterService);
    }

    private static Club club(String id, boolean trusted) {
        Club c = new Club();
        c.setId(id);
        c.setName("Club " + id);
        c.setSlug("club-" + id);
        c.setTrusted(trusted);
        return c;
    }

    private static User user(String id, String email, UserRole... roles) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setRoles(new ArrayList<>(List.of(roles)));
        return u;
    }

    private static ClubMember membership(Club club, User user, ClubMemberRole role) {
        ClubMember m = new ClubMember();
        m.setId("m-" + club.getId() + "-" + user.getId());
        m.setClub(club);
        m.setUser(user);
        m.setRole(role);
        return m;
    }

    // ------------------------------------------------------- membership roles

    @Test
    void addingAMemberGrantsRoleClub() {
        Club club = club("c1", true);
        User user = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER);
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(user));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.empty());
        when(memberRepo.findByClub_IdOrderByCreatedAtAsc("c1")).thenReturn(List.of());

        service().addMember("c1", new AdminClubMemberRequest(" Legend@CurtinHonestly.test ", "owner"));

        ArgumentCaptor<ClubMember> captor = ArgumentCaptor.forClass(ClubMember.class);
        verify(memberRepo).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(ClubMemberRole.OWNER);
        assertThat(captor.getValue().getClub()).isSameAs(club);
        assertThat(user.getRoles()).containsExactly(UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        verify(userRepo).save(user);
    }

    @Test
    void addingAnExistingMemberChangesTheirRoleWithoutDuplicatingRoleClub() {
        Club club = club("c1", true);
        User user = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember existing = membership(club, user, ClubMemberRole.EDITOR);
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(user));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.of(existing));
        when(memberRepo.findByClub_IdOrderByCreatedAtAsc("c1")).thenReturn(List.of(existing));

        AdminClubDTO dto = service().addMember("c1", new AdminClubMemberRequest("legend@curtinhonestly.test", "OWNER"));

        assertThat(existing.getRole()).isEqualTo(ClubMemberRole.OWNER);
        assertThat(user.getRoles()).containsExactly(UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        verify(userRepo, never()).save(any());
        assertThat(dto.members()).hasSize(1);
        assertThat(dto.members().get(0).email()).isEqualTo("legend@curtinhonestly.test");
    }

    @Test
    void addingAnEmailWithNoAccountIsANotFoundThatTellsTheAdminWhatToDo() {
        Club club = club("c1", true);
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(userRepo.findByEmail("president@comssa.org.au")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addMember("c1", new AdminClubMemberRequest("president@comssa.org.au", null)))
                .isInstanceOf(ClubNotFoundException.class)
                .hasMessageContaining("sign up");
        verify(memberRepo, never()).save(any());
    }

    @Test
    void removingTheLastMembershipRevokesRoleClub() {
        Club club = club("c1", true);
        User user = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember existing = membership(club, user, ClubMemberRole.OWNER);
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.of(existing));
        when(memberRepo.countByUser_Id("u1")).thenReturn(0L);
        when(memberRepo.findByClub_IdOrderByCreatedAtAsc("c1")).thenReturn(List.of());

        service().removeMember("c1", "u1");

        verify(memberRepo).delete(existing);
        assertThat(user.getRoles()).containsExactly(UserRole.ROLE_USER);
        verify(userRepo).save(user);
    }

    @Test
    void removingOneOfSeveralMembershipsKeepsRoleClub() {
        Club club = club("c1", true);
        User user = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        ClubMember existing = membership(club, user, ClubMemberRole.EDITOR);
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1")).thenReturn(Optional.of(existing));
        when(memberRepo.countByUser_Id("u1")).thenReturn(1L);
        when(memberRepo.findByClub_IdOrderByCreatedAtAsc("c1")).thenReturn(List.of());

        service().removeMember("c1", "u1");

        assertThat(user.getRoles()).containsExactly(UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        verify(userRepo, never()).save(any());
    }

    // ---------------------------------------------------------- portal access

    @Test
    void nonMembersAreForbiddenAndUnknownClubsAreNotFound() {
        Club club = club("c1", true);
        User outsider = user("u9", "outsider@student.curtin.edu.au", UserRole.ROLE_USER);
        when(userRepo.findByEmail("outsider@student.curtin.edu.au")).thenReturn(Optional.of(outsider));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requireMember("c1", "outsider@student.curtin.edu.au"))
                .isInstanceOf(ClubForbiddenException.class);

        when(clubRepo.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().requireMember("nope", "outsider@student.curtin.edu.au"))
                .isInstanceOf(ClubNotFoundException.class);
    }

    @Test
    void editorsCanManageEventsButNotTheClubProfile() {
        Club club = club("c1", true);
        User editor = user("u2", "topreviewer@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        when(userRepo.findByEmail("topreviewer@curtinhonestly.test")).thenReturn(Optional.of(editor));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u2"))
                .thenReturn(Optional.of(membership(club, editor, ClubMemberRole.EDITOR)));

        ClubService.Access access = service().requireMember("c1", "topreviewer@curtinhonestly.test");
        assertThat(access.role()).isEqualTo(ClubMemberRole.EDITOR);
        assertThat(access.owner()).isFalse();

        assertThatThrownBy(() -> service().updateProfile("c1", "topreviewer@curtinhonestly.test",
                new ClubProfileUpdateRequest("New blurb", null, null, null)))
                .isInstanceOf(ClubForbiddenException.class)
                .hasMessageContaining("owner");
        verify(clubRepo, never()).save(any());
    }

    @Test
    void ownersEditTheProfileAndLinksAreNormalised() {
        Club club = club("c1", true);
        User owner = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(owner));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));
        when(memberRepo.findByClub_IdAndUser_Id("c1", "u1"))
                .thenReturn(Optional.of(membership(club, owner, ClubMemberRole.OWNER)));
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        when(clubRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ClubPortalClubDTO dto = service().updateProfile("c1", "legend@curtinhonestly.test",
                new ClubProfileUpdateRequest(" Revision sessions for every first-year computing unit. ",
                        "comssa.org.au", "", " Hello@ComSSA.org.au "));

        assertThat(dto.description()).isEqualTo("Revision sessions for every first-year computing unit.");
        assertThat(dto.websiteUrl()).isEqualTo("https://comssa.org.au");
        assertThat(dto.logoUrl()).isNull();
        assertThat(dto.contactEmail()).isEqualTo("hello@comssa.org.au");
        assertThat(dto.role()).isEqualTo("OWNER");
    }

    @Test
    void adminsActAsOwnerOfEveryClub() {
        Club club = club("c1", false);
        User admin = user("a1", "admin@curtinhonestly.com", UserRole.ROLE_USER, UserRole.ROLE_ADMIN);
        when(userRepo.findByEmail("admin@curtinhonestly.com")).thenReturn(Optional.of(admin));
        when(clubRepo.findById("c1")).thenReturn(Optional.of(club));

        ClubService.Access access = service().requireOwner("c1", "admin@curtinhonestly.com");

        assertThat(access.admin()).isTrue();
        assertThat(access.owner()).isTrue();
        verify(memberRepo, never()).findByClub_IdAndUser_Id(any(), any());
    }

    @Test
    void clubsForListsMembershipsWithRoles() {
        Club comssa = club("c1", true);
        Club unipass = club("c2", true);
        User user = user("u1", "legend@curtinhonestly.test", UserRole.ROLE_USER, UserRole.ROLE_CLUB);
        when(userRepo.findByEmail("legend@curtinhonestly.test")).thenReturn(Optional.of(user));
        when(memberRepo.findByUser_IdOrderByCreatedAtAsc("u1")).thenReturn(List.of(
                membership(comssa, user, ClubMemberRole.OWNER),
                membership(unipass, user, ClubMemberRole.EDITOR)));

        List<ClubPortalClubDTO> clubs = service().clubsFor("legend@curtinhonestly.test");

        assertThat(clubs).extracting(ClubPortalClubDTO::slug).containsExactly("club-c1", "club-c2");
        assertThat(clubs).extracting(ClubPortalClubDTO::role).containsExactly("OWNER", "EDITOR");
    }

    // ------------------------------------------------------------ admin CRUD

    @Test
    void createDerivesASlugFromTheNameAndRejectsDuplicates() {
        when(clubRepo.existsByNameIgnoreCase("Curtin Data Science Society")).thenReturn(false);
        when(clubRepo.existsBySlug("curtin-data-science-society")).thenReturn(false);
        when(profanityFilterService.containsProfanity(any())).thenReturn(false);
        when(clubRepo.save(any())).thenAnswer(inv -> {
            Club c = inv.getArgument(0);
            c.setId("new");
            return c;
        });
        when(memberRepo.findByClub_IdOrderByCreatedAtAsc("new")).thenReturn(List.of());

        AdminClubDTO dto = service().create(new AdminClubUpsertRequest(" Curtin Data Science Society ", "  ",
                null, null, null, null, null, null));

        assertThat(dto.slug()).isEqualTo("curtin-data-science-society");
        assertThat(dto.trusted()).isFalse();
        assertThat(dto.active()).isTrue();

        when(clubRepo.existsByNameIgnoreCase("ComSSA")).thenReturn(true);
        assertThatThrownBy(() -> service().create(new AdminClubUpsertRequest("ComSSA", null, null, null, null, null, true, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void slugsAreNormalisedToLowerCaseHandles() {
        assertThat(ClubService.normaliseSlug("ComSSA")).isEqualTo("comssa");
        assertThat(ClubService.normaliseSlug("  Curtin Data Science Society! ")).isEqualTo("curtin-data-science-society");
        assertThat(ClubService.normaliseSlug("uni_pass")).isEqualTo("uni-pass");
        assertThatThrownBy(() -> ClubService.normaliseSlug("!!!")).isInstanceOf(IllegalArgumentException.class);
    }
}
