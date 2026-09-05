package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.BoardAdminFlaggedItemDTO;
import com.curtinhonestly.backend.dto.BoardAdminPostDTO;
import com.curtinhonestly.backend.dto.BoardAdminThreadDTO;
import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.BoardAdminService;
import com.curtinhonestly.backend.service.BoardNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Board moderation. /admin/** is admin-only in SecurityConfig; the class-level guard is belt and braces. */
@ConditionalOnProperty(prefix = "app.boards", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/admin/boards")
@RequiredArgsConstructor
@PreAuthorize(SecurityConstants.HAS_ROLE_ADMIN)
public class AdminBoardResource {

    private final BoardAdminService boardAdminService;

    @GetMapping("/flags")
    public ResponseEntity<List<BoardAdminFlaggedItemDTO>> flaggedContent() {
        return ResponseEntity.ok(boardAdminService.flaggedContent());
    }

    @GetMapping("/threads")
    public ResponseEntity<Page<BoardAdminThreadDTO>> listThreads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(boardAdminService.listThreads(page, size));
    }

    @GetMapping("/posts")
    public ResponseEntity<Page<BoardAdminPostDTO>> listPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(boardAdminService.listPosts(page, size));
    }

    @PatchMapping("/threads/{id}/lock")
    public ResponseEntity<BoardAdminThreadDTO> lock(@PathVariable String id) {
        return ResponseEntity.ok(boardAdminService.setLocked(id, true));
    }

    @PatchMapping("/threads/{id}/unlock")
    public ResponseEntity<BoardAdminThreadDTO> unlock(@PathVariable String id) {
        return ResponseEntity.ok(boardAdminService.setLocked(id, false));
    }

    @PatchMapping("/threads/{id}/pin")
    public ResponseEntity<BoardAdminThreadDTO> pin(@PathVariable String id) {
        return ResponseEntity.ok(boardAdminService.setPinned(id, true));
    }

    @PatchMapping("/threads/{id}/unpin")
    public ResponseEntity<BoardAdminThreadDTO> unpin(@PathVariable String id) {
        return ResponseEntity.ok(boardAdminService.setPinned(id, false));
    }

    // Remove = soft delete + clear flags.
    @DeleteMapping("/threads/{id}")
    public ResponseEntity<Void> removeThread(@PathVariable String id) {
        boardAdminService.removeThread(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> removePost(@PathVariable String id) {
        boardAdminService.removePost(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/threads/{id}/flags")
    public ResponseEntity<Void> dismissThreadFlags(@PathVariable String id) {
        boardAdminService.dismissThreadFlags(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}/flags")
    public ResponseEntity<Void> dismissPostFlags(@PathVariable String id) {
        boardAdminService.dismissPostFlags(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BoardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }
}
