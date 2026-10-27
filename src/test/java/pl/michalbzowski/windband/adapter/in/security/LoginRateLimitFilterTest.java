package pl.michalbzowski.windband.adapter.in.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pentest P2 (A01): brak rate limiting na /api/auth/login — throttling brute-force.
 */
class LoginRateLimitFilterTest {

    private LoginRateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;
    private boolean chainCalled = false;

    @BeforeEach
    void setUp() {
        filter = new LoginRateLimitFilter(3, 60);
        request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.7");
        response = new MockHttpServletResponse();
        chain = (req, res) -> chainCalled = true;
    }

    @Test
    @DisplayName("P2: prób do limitu przepuszcza (chain wywoływany)")
    void underLimit_passesThrough() throws Exception {
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("P2: próba ponad limit → 429 + Retry-After, chain NIE wywoływany")
    void overLimit_returns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }
        chainCalled = false;
        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("Too many login attempts");
    }

    @Test
    @DisplayName("P2: inne IP mają własny limit (klucz per adres)")
    void differentIp_hasOwnBucket() throws Exception {
        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/auth/login");
        other.setRemoteAddr("198.51.100.2");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        boolean[] otherChainCalled = {false};
        FilterChain otherChain = (req, res) -> otherChainCalled[0] = true;

        filter.doFilter(other, otherResponse, otherChain);

        assertThat(otherChainCalled[0]).isTrue();
        assertThat(otherResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("P2: GET i inne ścieżki nie są limitowane")
    void nonProtectedRequests_skipped() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/auth/login");
        get.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(get, r2, chain);

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/events");
        other.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse r3 = new MockHttpServletResponse();
        filter.doFilter(other, r3, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("P2: X-Forwarded-For (Cloudflare Tunnel) wygrywa przy kluczu")
    void forwardedFor_usesFirstHop() throws Exception {
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");

        for (int i = 0; i < 3; i++) {
            filter.doFilter(request, response, chain);
        }
        chainCalled = false;
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }
}
