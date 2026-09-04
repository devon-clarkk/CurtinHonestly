package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.ClubEventStatus;
import com.curtinhonestly.backend.dto.AdminClubDTO;
import com.curtinhonestly.backend.dto.AdminClubMemberRequest;
import com.curtinhonestly.backend.dto.AdminClubUpsertRequest;
import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.ClubEventManageDTO;
import com.curtinhonestly.backend.dto.ClubEventOptionsDTO;
import com.curtinhonestly.backend.dto.ClubEventRejectRequest;
import com.curtinhonestly.backend.dto.ClubEventUpsertRequest;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.ClubEventService;
import com.curtinhonestly.backend.service.ClubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Admin management of clubs, their members and the event moderation queue.
 * The whole /admin/** namespace is already ROLE_ADMIN in SecurityConfig; the
 * class-level @PreAuthorize is belt and braces. Clubs live under
 * /admin/clubs, events under /admin/club-events so the two id spaces never
 * collide.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminClubResource {

    private final ClubService clubService;
    private final ClubEventService eventService;

    // ----------------------------------------------------------------- clubs

    @GetMapping("/clubs")
    public ResponseEntity<List<AdminClubDTO>> clubs() {
        return ResponseEntity.ok(clubService.listAll());
    }

    @PostMapping("/clubs")
    public ResponseEntity<AdminClubDTO> createClub(@RequestBody AdminClubUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clubService.create(request));
    }

    @PutMapping("/clubs/{id}")
    public ResponseEntity<AdminClubDTO> updateClub(@PathVariable String id, @RequestBody AdminClubUpsertRequest request) {
        return ResponseEntity.ok(clubService.update(id, request));
    }

    @DeleteMapping("/clubs/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable String id) {
        clubService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Adds an existing account by email (granting ROLE_CLUB). 404 with a clear
     * message when no account exists yet, so the admin knows to have the club
     * sign up first.
     */
    @PostMapping("/clubs/{id}/members")
    public ResponseEntity<AdminClubDTO> addMember(@PathVariable String id, @RequestBody AdminClubMemberRequest request) {
        return ResponseEntity.ok(clubService.addMember(id, request));
    }

    @PutMapping("/clubs/{id}/members/{userId}")
    public ResponseEntity<AdminClubDTO> setMemberRole(@PathVariable String id,
                                                      @PathVariable String userId,
                                                      @RequestBody AdminClubMemberRequest request) {
        return ResponseEntity.ok(clubService.setMemberRole(id, userId, request));
    }

    @DeleteMapping("/clubs/{id}/members/{userId}")
    public ResponseEntity<AdminClubDTO> removeMember(@PathVariable String id, @PathVariable String userId) {
        return ResponseEntity.ok(clubService.removeMember(id, userId));
    }

    /** Admins post on behalf of any club; the event is published immediately. */
    @PostMapping("/clubs/{id}/events")
    public ResponseEntity<ClubEventManageDTO> createEvent(@PathVariable String id,
                                                          @RequestBody ClubEventUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(eventService.adminCreate(id, request));
    }

    // ---------------------------------------------------------------- events

    /** All events, newest first; pass ?status=PENDING (or any status) and/or ?clubId= to filter. */
    @GetMapping("/club-events")
    public ResponseEntity<List<ClubEventManageDTO>> events(@RequestParam(required = false) String status,
                                                           @RequestParam(required = false) String clubId) {
        ClubEventStatus filter = status == null || status.isBlank() ? null : ClubEventStatus.parse(status);
        return ResponseEntity.ok(eventService.adminList(filter, clubId));
    }

    @GetMapping("/club-events/options")
    public ResponseEntity<ClubEventOptionsDTO> options() {
        return ResponseEntity.ok(eventService.options());
    }

    /** Dry run of a targeting rule: how many units it matches and a sample of their codes. */
    @GetMapping("/club-events/preview")
    public ResponseEntity<AdminUnitResourcePreviewDTO> preview(@RequestParam(required = false) String codePrefixes,
                                                               @RequestParam(required = false) String faculty,
                                                               @RequestParam(required = false) String level,
                                                               @RequestParam(required = false) String unitCode) {
        return ResponseEntity.ok(eventService.preview(codePrefixes, faculty, level, unitCode));
    }

    @PutMapping("/club-events/{id}")
    public ResponseEntity<ClubEventManageDTO> updateEvent(@PathVariable String id,
                                                          @RequestBody ClubEventUpsertRequest request) {
        return ResponseEntity.ok(eventService.adminUpdate(id, request));
    }

    @PostMapping("/club-events/{id}/approve")
    public ResponseEntity<ClubEventManageDTO> approve(@PathVariable String id) {
        return ResponseEntity.ok(eventService.approve(id));
    }

    @PostMapping("/club-events/{id}/reject")
    public ResponseEntity<ClubEventManageDTO> reject(@PathVariable String id,
                                                     @RequestBody(required = false) ClubEventRejectRequest request) {
        return ResponseEntity.ok(eventService.reject(id, request == null ? null : request.reason()));
    }

    @PostMapping("/club-events/{id}/cancel")
    public ResponseEntity<ClubEventManageDTO> cancel(@PathVariable String id) {
        return ResponseEntity.ok(eventService.adminCancel(id));
    }

    @DeleteMapping("/club-events/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        eventService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}
