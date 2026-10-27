package pl.michalbzowski.windband.adapter.in.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pentest P2 (A01): brak rate limiting na auth-endpointach — /api/auth/login
 * (form-login, BCrypt) i /api/auth/register-team bez throttlingu → ryzyko
 * brute-force. Prosty licznik stałego okna czasowego per IP: bez nowej
 * zależności; stan in-memory (Railway = pojedyncza instancja; przy kilku
 * instancjach/shared store trzeba będzie to rozszerzyć — poza zakresem tego fixu).
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS =
            Set.of("/api/auth/login", "/api/auth/register-team");

    private final int maxAttemptsPerWindow;
    private final long windowSeconds;
    private final Map<String, AtomicLong> windowStarts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> attemptCounts = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(int maxAttemptsPerWindow, long windowSeconds) {
        this.maxAttemptsPerWindow = maxAttemptsPerWindow;
        this.windowSeconds = windowSeconds;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        for (String protectedPath : PROTECTED_PATHS) {
            if (uri.startsWith(protectedPath)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        purgeStaleEntries();

        String key = clientAddress(request) + "|" + request.getRequestURI();
        long now = System.currentTimeMillis();

        AtomicLong start = windowStarts.computeIfAbsent(key, k -> new AtomicLong(now));
        if (now - start.get() > windowSeconds * 1000L) {
            start.set(now);
            attemptCounts.put(key, new AtomicInteger(0));
        }
        int attempts = attemptCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (attempts > maxAttemptsPerWindow) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType("application/json;charset=UTF-8");
            // Celowo generyczna odpowiedź — brak orakla „ile prób zostało”.
            response.getWriter().write("{\"error\":\"Too many login attempts. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /** Pierwszy adres z X-Forwarded-For (za Cloudflare Tunnel), w przeciwnym razie socket. */
    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.substring(0, forwarded.indexOf(',') < 0 ? forwarded.length() : forwarded.indexOf(','))
                    .trim();
        }
        return request.getRemoteAddr();
    }

    private void purgeStaleEntries() {
        long now = System.currentTimeMillis();
        windowStarts.entrySet().removeIf(e -> now - e.getValue().get() > windowSeconds * 2000L);
        attemptCounts.keySet().retainAll(windowStarts.keySet());
    }

    /** DLA TESTÓW: reset okien licznika. */
    void reset() {
        windowStarts.clear();
        attemptCounts.clear();
    }
}
