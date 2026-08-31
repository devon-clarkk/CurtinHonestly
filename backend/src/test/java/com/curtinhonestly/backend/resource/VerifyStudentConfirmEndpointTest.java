package com.curtinhonestly.backend.resource;

import com.curtinhonestly.backend.config.TestcontainersConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security audit finding #5: the student-verification confirm endpoint takes its
 * single-use token in a POST body instead of a URL query string, so the token does
 * not travel through Referer headers, browser history, or proxy and access logs.
 *
 * <p>These assertions are about the route's shape, which is easy to break silently:
 * the endpoint has to stay reachable without a session (people open the emailed link
 * on whatever device is handy), and the old GET has to be gone rather than left
 * behind as a second, leakier way in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class VerifyStudentConfirmEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmAcceptsAnUnauthenticatedPostAndReadsTheTokenFromTheBody() throws Exception {
        // No Authorization header. A 400 ("this verification link is invalid") means the
        // request reached the controller and the token was bound from the body; a 401 or
        // 403 would mean the SecurityConfig rule for the new POST route is missing and
        // every emailed link is broken.
        mockMvc.perform(post("/auth/verify-student/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "not-a-real-token"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmRejectsAnEmptyTokenAsABadRequestRatherThanAnError() throws Exception {
        mockMvc.perform(post("/auth/verify-student/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOldQueryStringGetIsGone() throws Exception {
        // Leaving the GET in place would keep finding #5 open: it put a live single-use
        // token in a URL and answered with a session. Anything but a 2xx proves it no
        // longer confirms anything (Spring answers 405 for a known path, wrong method).
        mockMvc.perform(get("/auth/verify-student/confirm").param("token", "not-a-real-token"))
                .andExpect(status().is4xxClientError());
    }
}
