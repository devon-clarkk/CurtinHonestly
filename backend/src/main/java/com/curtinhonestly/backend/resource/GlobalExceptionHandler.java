package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.ErrorResponse;
import com.curtinhonestly.backend.service.CampaignEntryTokenExhaustedException;
import com.curtinhonestly.backend.service.ClubForbiddenException;
import com.curtinhonestly.backend.service.ClubNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    // Club and event lookups (ClubService, ClubEventService). Declared here rather than
    // per controller because three controllers (public, portal, admin) share them.
    @ExceptionHandler(ClubNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleClubNotFound(ClubNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(ClubForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleClubForbidden(ClubForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(CampaignEntryTokenExhaustedException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(CampaignEntryTokenExhaustedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message.isBlank() ? "Invalid request." : message));
    }

    // A request for a path no controller serves (a mistyped URL, or a feature such as the
    // boards whose controllers are switched off by app.boards.enabled) must be a 404, not
    // a 500 from the catch-all below.
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("Not found."));
    }

    // Catch-all: never leak exception details to the client, log them server-side instead.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("An unexpected error occurred. Please try again later."));
    }
}
