package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.ResourceStatus;
import com.curtinhonestly.backend.dto.AdminUnitResourceLinkDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourceOptionsDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourcePreviewDTO;
import com.curtinhonestly.backend.dto.AdminUnitResourceReorderRequest;
import com.curtinhonestly.backend.dto.AdminUnitResourceUpsertRequest;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.UnitResourceLinkService;
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
 * Admin management of unit resources. The whole /admin/** namespace is already
 * ROLE_ADMIN in SecurityConfig; the class-level @PreAuthorize is belt and braces.
 */
@RestController
@RequestMapping("/admin/unit-resources")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminUnitResourceLinkResource {

    private final UnitResourceLinkService service;

    /** All rows, newest first; pass ?status=PENDING (or APPROVED, REJECTED) to filter. */
    @GetMapping
    public ResponseEntity<List<AdminUnitResourceLinkDTO>> list(@RequestParam(required = false) String status) {
        ResourceStatus filter = status == null || status.isBlank() ? null : ResourceStatus.parse(status);
        return ResponseEntity.ok(service.listAll(filter));
    }

    @GetMapping("/options")
    public ResponseEntity<AdminUnitResourceOptionsDTO> options() {
        return ResponseEntity.ok(service.options());
    }

    /** Dry run of a targeting rule: how many units it matches and a sample of their codes. */
    @GetMapping("/preview")
    public ResponseEntity<AdminUnitResourcePreviewDTO> preview(@RequestParam(required = false) String codePrefixes,
                                                               @RequestParam(required = false) String faculty,
                                                               @RequestParam(required = false) String level,
                                                               @RequestParam(required = false) String unitCode) {
        return ResponseEntity.ok(service.preview(codePrefixes, faculty, level, unitCode));
    }

    @PostMapping
    public ResponseEntity<AdminUnitResourceLinkDTO> create(@RequestBody AdminUnitResourceUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody AdminUnitResourceReorderRequest request) {
        service.reorder(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminUnitResourceLinkDTO> update(@PathVariable String id,
                                                           @RequestBody AdminUnitResourceUpsertRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminUnitResourceLinkDTO> approve(@PathVariable String id) {
        return ResponseEntity.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminUnitResourceLinkDTO> reject(@PathVariable String id) {
        return ResponseEntity.ok(service.reject(id));
    }
}
