package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.ClubEventManageDTO;
import com.curtinhonestly.backend.dto.ClubEventOptionsDTO;
import com.curtinhonestly.backend.dto.ClubEventUpsertRequest;
import com.curtinhonestly.backend.dto.ClubPortalClubDTO;
import com.curtinhonestly.backend.dto.ClubProfileUpdateRequest;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.ClubEventService;
import com.curtinhonestly.backend.service.ClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The club portal: signed-in club members manage their own club's profile and
 * events. /club/** is ROLE_CLUB or ROLE_ADMIN in SecurityConfig; the
 * class-level @PreAuthorize is belt and braces. Which club a caller may act
 * for is checked per request in ClubService (non-members get 403, EDITORs
 * cannot edit the profile). Writes are rate limited as POST /club in
 * RateLimitFilter.
 */
@RestController
@RequestMapping("/club")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.IS_CLUB_OR_ADMIN)
public class ClubPortalResource {

    private final ClubService clubService;
    private final ClubEventService eventService;

    /** The clubs the caller belongs to, with their role in each. */
    @GetMapping("/me")
    public ResponseEntity<List<ClubPortalClubDTO>> me(Authentication authentication) {
        return ResponseEntity.ok(clubService.clubsFor(authentication.getName()));
    }

    @GetMapping("/options")
    public ResponseEntity<ClubEventOptionsDTO> options() {
        return ResponseEntity.ok(eventService.options());
    }

    /** OWNER only: description, website, logo and contact. */
    @PutMapping("/{clubId}")
    public ResponseEntity<ClubPortalClubDTO> updateProfile(@PathVariable String clubId,
                                                           @RequestBody ClubProfileUpdateRequest request,
                                                           Authentication authentication) {
        return ResponseEntity.ok(clubService.updateProfile(clubId, authentication.getName(), request));
    }

    /** Every event of the club, all statuses, latest start first. */
    @GetMapping("/{clubId}/events")
    public ResponseEntity<List<ClubEventManageDTO>> events(@PathVariable String clubId, Authentication authentication) {
        return ResponseEntity.ok(eventService.portalEvents(clubId, authentication.getName()));
    }

    /** Dry run of a targeting rule: how many units it matches and a sample of their codes. */
    @GetMapping("/{clubId}/events/preview")
    public ResponseEntity<AdminUnitResourcePreviewDTO> preview(@PathVariable String clubId,
                                                               @RequestParam(required = false) String codePrefixes,
                                                               @RequestParam(required = false) String faculty,
                                                               @RequestParam(required = false) String level,
                                                               @RequestParam(required = false) String unitCode,
                                                               Authentication authentication) {
        clubService.requireMember(clubId, authentication.getName());
        return ResponseEntity.ok(eventService.preview(codePrefixes, faculty, level, unitCode));
    }

    /** Creates a DRAFT. Publish it separately. */
    @PostMapping("/{clubId}/events")
    public ResponseEntity<ClubEventManageDTO> create(@PathVariable String clubId,
                                                     @RequestBody ClubEventUpsertRequest request,
                                                     Authentication authentication) {
        ClubEventManageDTO created = eventService.portalCreate(clubId, authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{clubId}/events/{id}")
    public ResponseEntity<ClubEventManageDTO> update(@PathVariable String clubId,
                                                     @PathVariable String id,
                                                     @RequestBody ClubEventUpsertRequest request,
                                                     Authentication authentication) {
        return ResponseEntity.ok(eventService.portalUpdate(clubId, authentication.getName(), id, request));
    }

    /** Trusted club: live now. Untrusted club: queued for an admin. */
    @PostMapping("/{clubId}/events/{id}/publish")
    public ResponseEntity<ClubEventManageDTO> publish(@PathVariable String clubId,
                                                      @PathVariable String id,
                                                      Authentication authentication) {
        return ResponseEntity.ok(eventService.portalPublish(clubId, authentication.getName(), id));
    }

    @PostMapping("/{clubId}/events/{id}/cancel")
    public ResponseEntity<ClubEventManageDTO> cancel(@PathVariable String clubId,
                                                     @PathVariable String id,
                                                     Authentication authentication) {
        return ResponseEntity.ok(eventService.portalCancel(clubId, authentication.getName(), id));
    }

    /** Drafts only. */
    @DeleteMapping("/{clubId}/events/{id}")
    public ResponseEntity<Void> delete(@PathVariable String clubId,
                                       @PathVariable String id,
                                       Authentication authentication) {
        eventService.portalDelete(clubId, authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
