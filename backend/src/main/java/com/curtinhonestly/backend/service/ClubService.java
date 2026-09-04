package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.Club;
import com.curtinhonestly.backend.domain.ClubEventStatus;
import com.curtinhonestly.backend.domain.ClubMember;
import com.curtinhonestly.backend.domain.ClubMemberRole;
import com.curtinhonestly.backend.domain.User;
import com.curtinhonestly.backend.domain.UserRole;
import com.curtinhonestly.backend.dto.AdminClubDTO;
import com.curtinhonestly.backend.dto.AdminClubMemberDTO;
import com.curtinhonestly.backend.dto.AdminClubMemberRequest;
import com.curtinhonestly.backend.dto.AdminClubUpsertRequest;
import com.curtinhonestly.backend.dto.ClubPortalClubDTO;
import com.curtinhonestly.backend.dto.ClubProfileUpdateRequest;
import com.curtinhonestly.backend.repo.ClubEventRepo;
import com.curtinhonestly.backend.repo.ClubMemberRepo;
import com.curtinhonestly.backend.repo.ClubRepo;
import com.curtinhonestly.backend.repo.UserRepo;
import com.curtinhonestly.backend.util.EmailNormalizer;
import com.curtinhonestly.backend.util.SafeUrl;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Clubs and their members: admin management, the per-club access checks the
 * portal relies on, and the ROLE_CLUB bookkeeping that opens the portal.
 *
 * ROLE_CLUB is derived state. Adding a user to any club grants it; removing
 * their last membership revokes it. Authorities are loaded from the database
 * on every request (JwtAuthenticationFilter), so a grant is effective on the
 * next call without a new sign-in.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
public class ClubService {

    private static final Pattern SLUG_SHAPE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ClubRepo clubRepo;
    private final ClubMemberRepo memberRepo;
    private final ClubEventRepo eventRepo;
    private final UserRepo userRepo;
    private final ProfanityFilterService profanityFilterService;

    /** What the caller may do for one club. Admins act as OWNER of every club. */
    public record Access(Club club, User user, ClubMemberRole role, boolean admin) {
        public boolean owner() {
            return admin || role == ClubMemberRole.OWNER;
        }
    }

    // --------------------------------------------------------------- access

    /**
     * The signed-in user's standing in a club. 404 when the club does not
     * exist, 403 when they are not a member (admins always pass).
     */
    public Access requireMember(String clubId, String email) {
        User user = userRepo.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new ClubForbiddenException("Sign in to manage a club."));
        Club club = clubRepo.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("Club not found."));
        if (isAdmin(user)) {
            return new Access(club, user, ClubMemberRole.OWNER, true);
        }
        ClubMember member = memberRepo.findByClub_IdAndUser_Id(clubId, user.getId())
                .orElseThrow(() -> new ClubForbiddenException("You are not a member of this club."));
        return new Access(club, user, member.getRole(), false);
    }

    public Access requireOwner(String clubId, String email) {
        Access access = requireMember(clubId, email);
        if (!access.owner()) {
            throw new ClubForbiddenException("Only a club owner can edit the club profile.");
        }
        return access;
    }

    // --------------------------------------------------------------- portal

    /** The clubs the signed-in user may manage, with their role in each. Admins see every club. */
    public List<ClubPortalClubDTO> clubsFor(String email) {
        User user = userRepo.findByEmail(EmailNormalizer.normalize(email))
                .orElseThrow(() -> new ClubForbiddenException("Sign in to manage a club."));
        if (isAdmin(user)) {
            return clubRepo.findAllByOrderByNameAsc().stream()
                    .map(c -> toPortalDTO(c, "ADMIN"))
                    .toList();
        }
        return memberRepo.findByUser_IdOrderByCreatedAtAsc(user.getId()).stream()
                .map(m -> toPortalDTO(m.getClub(), m.getRole().name()))
                .toList();
    }

    /** OWNER only: description, website, logo and contact. Name, slug and trust stay with admins. */
    public ClubPortalClubDTO updateProfile(String clubId, String email, ClubProfileUpdateRequest request) {
        Access access = requireOwner(clubId, email);
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        Club club = access.club();
        applyProfile(club, request.description(), request.websiteUrl(), request.logoUrl(), request.contactEmail());
        Club saved = clubRepo.save(club);
        log.info("Club {} profile updated by {}", saved.getId(), access.user().getId());
        return toPortalDTO(saved, access.admin() ? "ADMIN" : access.role().name());
    }

    // ---------------------------------------------------------------- admin

    public List<AdminClubDTO> listAll() {
        List<Club> clubs = clubRepo.findAllByOrderByNameAsc();
        return clubs.stream().map(this::toAdminDTO).toList();
    }

    public AdminClubDTO get(String clubId) {
        return toAdminDTO(find(clubId));
    }

    public AdminClubDTO create(AdminClubUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        String name = requireText(request.name(), "A club name", Club.MAX_NAME);
        if (clubRepo.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A club with that name already exists.");
        }
        String slug = normaliseSlug(request.slug() == null || request.slug().isBlank() ? name : request.slug());
        if (clubRepo.existsBySlug(slug)) {
            throw new IllegalArgumentException("The slug \"" + slug + "\" is already taken.");
        }

        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        applyProfile(club, request.description(), request.websiteUrl(), request.logoUrl(), request.contactEmail());
        club.setTrusted(Boolean.TRUE.equals(request.trusted()));
        club.setActive(request.active() == null || request.active());
        Club saved = clubRepo.save(club);
        log.info("Admin created club {} ({})", saved.getId(), saved.getSlug());
        return toAdminDTO(saved);
    }

    public AdminClubDTO update(String clubId, AdminClubUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("A request body is required.");
        }
        Club club = find(clubId);
        String name = requireText(request.name(), "A club name", Club.MAX_NAME);
        if (!name.equalsIgnoreCase(club.getName()) && clubRepo.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A club with that name already exists.");
        }
        String slug = normaliseSlug(request.slug() == null || request.slug().isBlank() ? name : request.slug());
        if (!slug.equals(club.getSlug()) && clubRepo.existsBySlug(slug)) {
            throw new IllegalArgumentException("The slug \"" + slug + "\" is already taken.");
        }
        club.setName(name);
        club.setSlug(slug);
        applyProfile(club, request.description(), request.websiteUrl(), request.logoUrl(), request.contactEmail());
        if (request.trusted() != null) {
            club.setTrusted(request.trusted());
        }
        if (request.active() != null) {
            club.setActive(request.active());
        }
        return toAdminDTO(clubRepo.save(club));
    }

    /** Removes the club, its memberships (revoking ROLE_CLUB where it was the last one) and its events. */
    public void delete(String clubId) {
        Club club = find(clubId);
        List<ClubMember> members = memberRepo.findByClub_IdOrderByCreatedAtAsc(clubId);
        List<User> users = members.stream().map(ClubMember::getUser).toList();
        memberRepo.deleteAll(members);
        memberRepo.flush();
        users.forEach(this::revokeClubRoleIfNoMemberships);
        clubRepo.delete(club);
        log.info("Admin deleted club {} ({}) with {} members", clubId, club.getSlug(), members.size());
    }

    /**
     * Adds an existing account to a club (or changes the role of a current
     * member) and grants ROLE_CLUB. An email with no account is a 404 with a
     * message that tells the admin what to do next.
     */
    public AdminClubDTO addMember(String clubId, AdminClubMemberRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("A member email is required.");
        }
        Club club = find(clubId);
        String email = EmailNormalizer.normalize(request.email());
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ClubNotFoundException(
                        "No CurtinHonestly account exists for " + email + ". Ask the club to sign up with that "
                                + "email first (Register on the public site), then add them here."));
        ClubMemberRole role = ClubMemberRole.parse(request.role());

        Optional<ClubMember> existing = memberRepo.findByClub_IdAndUser_Id(clubId, user.getId());
        if (existing.isPresent()) {
            existing.get().setRole(role);
            memberRepo.save(existing.get());
        } else {
            ClubMember member = new ClubMember();
            member.setClub(club);
            member.setUser(user);
            member.setRole(role);
            memberRepo.save(member);
        }
        grantClubRole(user);
        log.info("Admin added user {} to club {} as {}", user.getId(), clubId, role);
        return toAdminDTO(club);
    }

    public AdminClubDTO setMemberRole(String clubId, String userId, AdminClubMemberRequest request) {
        Club club = find(clubId);
        ClubMember member = memberRepo.findByClub_IdAndUser_Id(clubId, userId)
                .orElseThrow(() -> new ClubNotFoundException("That user is not a member of this club."));
        member.setRole(ClubMemberRole.parse(request == null ? null : request.role()));
        memberRepo.save(member);
        return toAdminDTO(club);
    }

    /** Removes a membership and, when it was the user's last one, ROLE_CLUB with it. */
    public AdminClubDTO removeMember(String clubId, String userId) {
        Club club = find(clubId);
        ClubMember member = memberRepo.findByClub_IdAndUser_Id(clubId, userId)
                .orElseThrow(() -> new ClubNotFoundException("That user is not a member of this club."));
        User user = member.getUser();
        memberRepo.delete(member);
        memberRepo.flush();
        revokeClubRoleIfNoMemberships(user);
        log.info("Admin removed user {} from club {}", userId, clubId);
        return toAdminDTO(club);
    }

    // ---------------------------------------------------------------- roles

    void grantClubRole(User user) {
        List<UserRole> roles = user.getRoles();
        if (roles == null) {
            roles = new ArrayList<>();
            user.setRoles(roles);
        }
        if (!roles.contains(UserRole.ROLE_CLUB)) {
            roles.add(UserRole.ROLE_CLUB);
            userRepo.save(user);
        }
    }

    void revokeClubRoleIfNoMemberships(User user) {
        if (user == null || user.getRoles() == null) {
            return;
        }
        if (memberRepo.countByUser_Id(user.getId()) > 0) {
            return;
        }
        if (user.getRoles().remove(UserRole.ROLE_CLUB)) {
            userRepo.save(user);
        }
    }

    private static boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().contains(UserRole.ROLE_ADMIN);
    }

    // -------------------------------------------------------------- helpers

    private Club find(String clubId) {
        return clubRepo.findById(clubId)
                .orElseThrow(() -> new ClubNotFoundException("Club not found."));
    }

    private void applyProfile(Club club, String description, String websiteUrl, String logoUrl, String contactEmail) {
        String cleanDescription = optionalText(description, "The description", Club.MAX_DESCRIPTION);
        if (profanityFilterService.containsProfanity(cleanDescription)) {
            throw new IllegalArgumentException("The description contains language that violates our community standards.");
        }
        club.setDescription(cleanDescription);
        club.setWebsiteUrl(optionalUrl(websiteUrl));
        club.setLogoUrl(optionalUrl(logoUrl));
        club.setContactEmail(optionalEmail(contactEmail));
    }

    /** Lower-case handle: letters, digits and single dashes, at most 60 characters. */
    static String normaliseSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > Club.MAX_SLUG) {
            slug = slug.substring(0, Club.MAX_SLUG).replaceAll("-+$", "");
        }
        if (slug.isEmpty() || !SLUG_SHAPE.matcher(slug).matches()) {
            throw new IllegalArgumentException("The slug must contain letters or digits, e.g. comssa or unipass.");
        }
        return slug;
    }

    private static String optionalUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return SafeUrl.normalise(raw);
    }

    private static String optionalEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String email = EmailNormalizer.normalize(raw);
        if (email.length() > Club.MAX_EMAIL || !EMAIL_SHAPE.matcher(email).matches()) {
            throw new IllegalArgumentException("That does not look like a valid contact email.");
        }
        return email;
    }

    private static String requireText(String raw, String what, int max) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(what + " is required.");
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(what + " must be " + max + " characters or fewer.");
        }
        return text;
    }

    private static String optionalText(String raw, String what, int max) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > max) {
            throw new IllegalArgumentException(what + " must be " + max + " characters or fewer.");
        }
        return text;
    }

    private static ClubPortalClubDTO toPortalDTO(Club c, String role) {
        return new ClubPortalClubDTO(
                c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getWebsiteUrl(), c.getLogoUrl(),
                c.getContactEmail(), c.isTrusted(), c.isActive(), role);
    }

    private AdminClubDTO toAdminDTO(Club c) {
        List<AdminClubMemberDTO> members = memberRepo.findByClub_IdOrderByCreatedAtAsc(c.getId()).stream()
                .map(m -> new AdminClubMemberDTO(m.getUser().getId(), m.getUser().getEmail(), m.getRole().name(), m.getCreatedAt()))
                .toList();
        return new AdminClubDTO(
                c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getWebsiteUrl(), c.getLogoUrl(),
                c.getContactEmail(), c.isTrusted(), c.isActive(), c.getCreatedAt(),
                eventRepo.countByClub_Id(c.getId()),
                eventRepo.countByClub_IdAndStatus(c.getId(), ClubEventStatus.PENDING),
                members);
    }
}
