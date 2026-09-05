package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test (no Spring context): a request for a path with no controller,
 * such as /boards/** in a deployment with app.boards.enabled=false, is a 404 with
 * the fixed message, not a 500 from the catch-all handler.
 */
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noResourceFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NoResourceFoundException(HttpMethod.GET, "/boards/general/threads"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Not found.");
    }

    @Test
    void noHandlerFoundMapsTo404() {
        ResponseEntity<ErrorResponse> response =
                handler.handleNotFound(new NoHandlerFoundException("GET", "/admin/boards/flags", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
