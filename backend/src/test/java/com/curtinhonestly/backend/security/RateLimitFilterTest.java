package com.curtinhonestly.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    // One trusted proxy hop, matching the Azure Container Apps ingress in front of the app.
    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter(), 1);

    private void hit(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
    }

    // Simulates an ingress that appends the real client IP to the right of any
    // client-supplied X-Forwarded-For, then dispatches the request through the filter.
    private MockHttpServletResponse hitBehindProxy(String method, String uri,
                                                   String spoofedXff, String realClientIp) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        String xff = spoofedXff == null ? realClientIp : spoofedXff + ", " + realClientIp;
        request.addHeader("X-Forwarded-For", xff);
        request.setRemoteAddr("10.0.0.1"); // ingress socket address
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void loginIsThrottledAfterTenRequestsPerMinute() throws Exception {
        for (int i = 0; i < 10; i++) {
            hit("POST", "/auth/login");
        }
        MockHttpServletRequest eleventh = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(eleventh, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    @Test
    void createReviewExactMatchDoesNotThrottleLikeEndpoint() throws Exception {
        // Exhaust the POST /reviews (create) limit for this IP...
        for (int i = 0; i < 20; i++) {
            hit("POST", "/reviews");
        }
        // ...but POST /reviews/{id}/likes is a distinct exact path and must still pass through.
        MockHttpServletRequest likeRequest = new MockHttpServletRequest("POST", "/reviews/abc-123/likes");
        likeRequest.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(likeRequest, response, chain);

        verify(chain).doFilter(likeRequest, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unrelatedRouteIsNeverThrottled() throws Exception {
        for (int i = 0; i < 50; i++) {
            hit("GET", "/units");
        }
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/units");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void tipCreationIsThrottledAcrossDifferentUnitsViaSuffixMatch() throws Exception {
        // The unit code segment varies per request, so this exercises the SUFFIX
        // matcher — all of these still share the same rate-limit bucket.
        for (int i = 0; i < 10; i++) {
            hit("POST", "/units/UNIT" + i + "/tips");
        }
        MockHttpServletRequest eleventh = new MockHttpServletRequest("POST", "/units/UNIT-NEW/tips");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(eleventh, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    @Test
    void tipDeletionIsNeverThrottled() throws Exception {
        for (int i = 0; i < 30; i++) {
            hit("DELETE", "/units/UNIT1/tips/tip-" + i);
        }
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/units/UNIT1/tips/tip-30");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void spoofedLeftmostXffDoesNotBypassRateLimit() throws Exception {
        // Same real client behind the ingress, but a different forged leftmost XFF on
        // every request. Pre-fix this rotated the rate-limit key and gave unlimited
        // logins; now the trusted (rightmost) entry pins them all to one bucket.
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse ok = hitBehindProxy("POST", "/auth/login", "9.9.9." + i, "203.0.113.7");
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse throttled = hitBehindProxy("POST", "/auth/login", "9.9.9.250", "203.0.113.7");
        assertThat(throttled.getStatus()).isEqualTo(429);
    }

    @Test
    void distinctRealClientsBehindProxyGetSeparateBuckets() throws Exception {
        // Two genuinely different clients (different rightmost/trusted entry) must not
        // share a bucket — the fix must not collapse everyone onto the proxy IP.
        for (int i = 0; i < 10; i++) {
            assertThat(hitBehindProxy("POST", "/auth/login", "1.2.3.4", "198.51.100.10").getStatus()).isEqualTo(200);
        }
        // A different real client still gets a fresh allowance.
        assertThat(hitBehindProxy("POST", "/auth/login", "1.2.3.4", "198.51.100.11").getStatus()).isEqualTo(200);
    }

    @Test
    void confirmingAVerificationLinkIsNotSwallowedByTheEmailSendLimit() throws Exception {
        // Finding #5 turned the confirm endpoint from GET into POST, which put it on the
        // same method as the /auth/verify-student PREFIX rule (5 per 10 minutes, there to
        // stop inbox-bombing). Matching is first-hit, so the confirm entry has to sit
        // above it; with the order reversed, the sixth person to click a legitimate link
        // in a ten-minute window gets a 429.
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/verify-student/confirm");
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void confirmingAVerificationLinkStillHasItsOwnCeiling() throws Exception {
        // Defence-in-depth against token guessing survives the move to POST: 20/minute.
        for (int i = 0; i < 20; i++) {
            hit("POST", "/auth/verify-student/confirm");
        }
        MockHttpServletRequest twentyFirst = new MockHttpServletRequest("POST", "/auth/verify-student/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(twentyFirst, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    @Test
    void sendingAVerificationEmailKeepsItsTighterLimit() throws Exception {
        // The other half of the ordering: exhausting the confirm bucket must not have
        // handed the email-send path a bigger allowance than the 5 it is meant to have.
        for (int i = 0; i < 5; i++) {
            hit("POST", "/auth/verify-student");
        }
        MockHttpServletRequest sixth = new MockHttpServletRequest("POST", "/auth/verify-student");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(sixth, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }

    @Test
    void reviewFlaggingIsThrottledAcrossDifferentReviewsViaSuffixMatch() throws Exception {
        for (int i = 0; i < 10; i++) {
            hit("POST", "/reviews/review-" + i + "/flags");
        }
        MockHttpServletRequest eleventh = new MockHttpServletRequest("POST", "/reviews/review-new/flags");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(eleventh, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        verifyNoInteractions(chain);
    }
}
