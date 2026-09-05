package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.ClubEventDTO;
import com.curtinhonestly.backend.dto.ClubProfileDTO;
import com.curtinhonestly.backend.dto.ClubSummaryDTO;
import com.curtinhonestly.backend.service.ClubEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public side of club events: the home strip, the unit page card, the /events
 * listing and detail, and the club directory. All GETs are permitAll in
 * SecurityConfig (GET /events/**, GET /clubs/**, and GET /units/** for the
 * unit card); the view beacon is a public POST rate limited in RateLimitFilter.
 */
@RestController
@RequiredArgsConstructor
public class ClubEventResource {

    private final ClubEventService service;

    /** Up to {@code limit} (default 4, max 12) upcoming events flagged for the home page, soonest first. */
    @GetMapping("/events/upcoming")
    public ResponseEntity<List<ClubEventDTO>> upcoming(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(service.upcomingForHome(limit));
    }

    /** Every upcoming published event, paged, optionally filtered to one club slug and one kind. */
    @GetMapping("/events")
    public ResponseEntity<Page<ClubEventDTO>> list(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String clubSlug,
                                                   @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(service.list(page, size, clubSlug, kind));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<ClubEventDTO> detail(@PathVariable String id) {
        return ResponseEntity.ok(service.detail(id));
    }

    /** Fire-and-forget view counter. 204 when counted, 404 for an unknown or unpublished id. */
    @PostMapping("/events/{id}/views")
    public ResponseEntity<Void> view(@PathVariable String id) {
        return service.recordView(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /** The unit page card: upcoming published events whose targeting covers the unit. 404 for an unknown code. */
    @GetMapping("/units/{code}/events")
    public ResponseEntity<List<ClubEventDTO>> forUnit(@PathVariable String code) {
        return service.forUnitCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/clubs")
    public ResponseEntity<List<ClubSummaryDTO>> clubs() {
        return ResponseEntity.ok(service.directory());
    }

    @GetMapping("/clubs/{slug}")
    public ResponseEntity<ClubProfileDTO> club(@PathVariable String slug) {
        return ResponseEntity.ok(service.profile(slug));
    }
}
