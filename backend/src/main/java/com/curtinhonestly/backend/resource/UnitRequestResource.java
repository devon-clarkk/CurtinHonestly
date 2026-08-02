package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.domain.UnitRequest;
import com.curtinhonestly.backend.dto.UnitRequestCreateRequest;
import com.curtinhonestly.backend.dto.UnitRequestDTO;
import com.curtinhonestly.backend.service.UnitRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/unit-requests")
@RequiredArgsConstructor
public class UnitRequestResource {

    private final UnitRequestService unitRequestService;

    @PostMapping
    public ResponseEntity<UnitRequestDTO> create(@Valid @RequestBody UnitRequestCreateRequest request) {
        UnitRequest saved = unitRequestService.create(request.requestedCode(), request.note());
        return ResponseEntity.ok(UnitRequestService.toDTO(saved));
    }
}
