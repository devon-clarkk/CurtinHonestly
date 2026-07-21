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

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter());

    private void hit(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
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
}
