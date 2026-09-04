package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.UnitResourceLinkDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkListDTO;
import com.curtinhonestly.backend.dto.UnitResourceLinkSuggestionRequest;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.UnitResourceLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public side of unit resources. Reads are covered by the permitAll
 * GET /units/** rule; the click beacon is opened up explicitly in
 * SecurityConfig and rate limited in RateLimitFilter; suggestions need a
 * signed-in student.
 */
@RestController
@RequestMapping("/units/{code}/resources")
@RequiredArgsConstructor
public class UnitResourceLinkResource {

    private final UnitResourceLinkService service;

    @GetMapping
    public ResponseEntity<UnitResourceLinkListDTO> list(@PathVariable String code) {
        return service.resourcesForUnitCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Fire-and-forget click counter. 204 when counted, 404 for an unknown or unapproved id. */
    @PostMapping("/{id}/clicks")
    public ResponseEntity<Void> click(@PathVariable String code, @PathVariable String id) {
        return service.recordClick(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/suggestions")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<UnitResourceLinkDTO> suggest(@PathVariable String code,
                                                       @Valid @RequestBody UnitResourceLinkSuggestionRequest request) {
        UnitResourceLinkDTO created = service.suggest(code, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
