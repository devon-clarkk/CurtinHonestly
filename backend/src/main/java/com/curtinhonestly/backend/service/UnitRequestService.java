package com.curtinhonestly.backend.service;

import com.curtinhonestly.backend.domain.UnitRequest;
import com.curtinhonestly.backend.dto.UnitRequestDTO;
import com.curtinhonestly.backend.repo.UnitRequestRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnitRequestService {

    private static final int MAX_CODE_LENGTH = 100;
    private static final int MAX_NOTE_LENGTH = 500;

    private final UnitRequestRepo unitRequestRepo;

    public UnitRequest create(String rawCode, String rawNote) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("Please enter a unit code or name.");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Unit code or name must be " + MAX_CODE_LENGTH + " characters or fewer.");
        }
        String note = rawNote == null ? null : rawNote.trim();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Note must be " + MAX_NOTE_LENGTH + " characters or fewer.");
        }

        UnitRequest request = new UnitRequest();
        request.setRequestedCode(code);
        request.setNote(note == null || note.isBlank() ? null : note);

        UnitRequest saved = unitRequestRepo.save(request);
        log.info("Unit requested: {}", code);
        return saved;
    }

    public List<UnitRequest> getAll() {
        return unitRequestRepo.findAllByOrderByCreatedAtDesc();
    }

    public void delete(String id) {
        unitRequestRepo.deleteById(id);
    }

    public static UnitRequestDTO toDTO(UnitRequest request) {
        return new UnitRequestDTO(request.getId(), request.getRequestedCode(), request.getNote(), request.getCreatedAt());
    }
}
