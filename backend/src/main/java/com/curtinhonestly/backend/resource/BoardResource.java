package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.BoardContentUpdateRequest;
import com.curtinhonestly.backend.dto.BoardFlagRequest;
import com.curtinhonestly.backend.dto.BoardPostCreateRequest;
import com.curtinhonestly.backend.dto.BoardPostDTO;
import com.curtinhonestly.backend.dto.BoardThreadCreateRequest;
import com.curtinhonestly.backend.dto.BoardThreadDetailDTO;
import com.curtinhonestly.backend.dto.BoardThreadSummaryDTO;
import com.curtinhonestly.backend.dto.BoardUnitSummaryDTO;
import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.security.SecurityConstants;
import com.curtinhonestly.backend.service.BoardForbiddenException;
import com.curtinhonestly.backend.service.BoardNotFoundException;
import com.curtinhonestly.backend.service.BoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Community boards. Reads are public (SecurityConfig permits GET /boards/**);
 * writes need a signed-in student, and edit/delete additionally require the
 * owner or an admin via BoardSecurityService.
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardResource {

    private static final String IS_ADMIN_OR_THREAD_OWNER =
            "hasRole('ADMIN') or @boardSecurityService.isThreadOwner(#id, authentication)";
    private static final String IS_ADMIN_OR_POST_OWNER =
            "hasRole('ADMIN') or @boardSecurityService.isPostOwner(#id, authentication)";

    private final BoardService boardService;

    // Reads

    @GetMapping("/general/threads")
    public ResponseEntity<Page<BoardThreadSummaryDTO>> listGeneralThreads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "activity") String sort) {
        return ResponseEntity.ok(boardService.listGeneralThreads(page, size, sort));
    }

    @GetMapping("/units/{code}/threads")
    public ResponseEntity<Page<BoardThreadSummaryDTO>> listUnitThreads(
            @PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "activity") String sort) {
        return ResponseEntity.ok(boardService.listUnitThreads(code, page, size, sort));
    }

    @GetMapping("/units/{code}/summary")
    public ResponseEntity<BoardUnitSummaryDTO> unitSummary(@PathVariable String code) {
        return ResponseEntity.ok(boardService.unitSummary(code));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<BoardThreadSummaryDTO>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(boardService.recentUnitThreads(limit));
    }

    @GetMapping("/threads/{id}")
    public ResponseEntity<BoardThreadDetailDTO> getThread(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(boardService.getThread(id, page, size));
    }

    // Writes

    @PostMapping("/general/threads")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<BoardThreadDetailDTO> createGeneralThread(@Valid @RequestBody BoardThreadCreateRequest request) {
        BoardThreadDetailDTO created = boardService.createGeneralThread(request.title(), request.body());
        return ResponseEntity.created(URI.create("/boards/threads/" + created.id())).body(created);
    }

    @PostMapping("/units/{code}/threads")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<BoardThreadDetailDTO> createUnitThread(@PathVariable String code,
                                                                 @Valid @RequestBody BoardThreadCreateRequest request) {
        BoardThreadDetailDTO created = boardService.createUnitThread(code, request.title(), request.body());
        return ResponseEntity.created(URI.create("/boards/threads/" + created.id())).body(created);
    }

    @PostMapping("/threads/{id}/posts")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<BoardPostDTO> createPost(@PathVariable String id,
                                                   @Valid @RequestBody BoardPostCreateRequest request) {
        BoardPostDTO created = boardService.createPost(id, request.body());
        return ResponseEntity.created(URI.create("/boards/threads/" + id)).body(created);
    }

    @PutMapping("/threads/{id}")
    @PreAuthorize(IS_ADMIN_OR_THREAD_OWNER)
    public ResponseEntity<BoardThreadDetailDTO> updateThread(@PathVariable String id,
                                                             @Valid @RequestBody BoardContentUpdateRequest request) {
        return ResponseEntity.ok(boardService.updateThread(id, request.title(), request.body()));
    }

    @PutMapping("/posts/{id}")
    @PreAuthorize(IS_ADMIN_OR_POST_OWNER)
    public ResponseEntity<BoardPostDTO> updatePost(@PathVariable String id,
                                                   @Valid @RequestBody BoardContentUpdateRequest request) {
        return ResponseEntity.ok(boardService.updatePost(id, request.body()));
    }

    @DeleteMapping("/threads/{id}")
    @PreAuthorize(IS_ADMIN_OR_THREAD_OWNER)
    public ResponseEntity<Void> deleteThread(@PathVariable String id) {
        boardService.deleteThread(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{id}")
    @PreAuthorize(IS_ADMIN_OR_POST_OWNER)
    public ResponseEntity<Void> deletePost(@PathVariable String id) {
        boardService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/threads/{id}/flags")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Void> flagThread(@PathVariable String id,
                                           @Valid @RequestBody(required = false) BoardFlagRequest request) {
        boardService.flagThread(id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/flags")
    @PreAuthorize(SecurityConstants.HAS_ROLE_USER)
    public ResponseEntity<Void> flagPost(@PathVariable String id,
                                         @Valid @RequestBody(required = false) BoardFlagRequest request) {
        boardService.flagPost(id, request == null ? null : request.reason());
        return ResponseEntity.noContent().build();
    }

    // Controller-local handlers win over GlobalExceptionHandler's catch-all,
    // which would otherwise turn these into 500s.

    @ExceptionHandler(BoardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BoardNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(BoardForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(BoardForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
    }
}
