package pl.michalbzowski.windband.infrastructure.superset;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

import javax.crypto.spec.SecretKeySpec;

/**
 * Client for Superset REST API.
 * Uses Keycloak access token for API calls (Bearer auth) and generates
 * guest tokens locally with HMAC-SHA256 (Superset-compatible JWT format).
 *
 * Superset API docs: https://superset.apache.org/docs/api/
 */
@Slf4j
@Component
public class SupersetClient {

    private final RestTemplate restTemplate;
    private final String supersetBaseUrl;
    private final String supersetUsername;
    private final String supersetPassword;
    private final String guestTokenJwtSecret;
    private final String keycloakUrl;
    private final String keycloakRealm;
    private final String keycloakSupersetClientId;
    private final String keycloakSupersetClientSecret;

    public SupersetClient(
            RestTemplate restTemplate,
            @Value("${superset.base-url:http://localhost:8088}") String supersetBaseUrl,
            @Value("${superset.username:admin}") String supersetUsername,
            @Value("${superset.password:admin}") String supersetPassword,
            @Value("${superset.guest-token-jwt-secret:WindbandGuestToken2026!ChangeMe#VeryLongSecretForSecurity}") String guestTokenJwtSecret,
            @Value("${keycloak.url:http://keycloak:8180}") String keycloakUrl,
            @Value("${keycloak.realm:windband}") String keycloakRealm,
            @Value("${keycloak.superset.client.id:superset}") String keycloakSupersetClientId,
            @Value("${keycloak.superset.client.secret:supersetsecret}") String keycloakSupersetClientSecret) {
        this.restTemplate = restTemplate;
        this.supersetBaseUrl = supersetBaseUrl.replaceAll("/$", "");
        this.supersetUsername = supersetUsername;
        this.supersetPassword = supersetPassword;
        this.guestTokenJwtSecret = guestTokenJwtSecret;
        this.keycloakUrl = keycloakUrl;
        this.keycloakRealm = keycloakRealm;
        this.keycloakSupersetClientId = keycloakSupersetClientId;
        this.keycloakSupersetClientSecret = keycloakSupersetClientSecret;
    }

    /**
     * Fetches all published dashboards from Superset.
     */
    public List<SupersetApiDtos.DashboardEntry> listDashboards() {
        String token = login();
        HttpHeaders headers = authHeaders(token);

        // Superset API v1: GET /api/v1/dashboard/
        ResponseEntity<SupersetApiDtos.DashboardListResponse> response = restTemplate.exchange(
                supersetBaseUrl + "/api/v1/dashboard/?q=(order_column:changed_on_delta_humanized,order_direction:desc)",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                SupersetApiDtos.DashboardListResponse.class
        );

        var body = response.getBody();
        if (body != null && body.getResult() != null) {
            return body.getResult();
        }
        return Collections.emptyList();
    }

    /**
     * Fetches a single dashboard by ID.
     */
    public SupersetApiDtos.DashboardDetail getDashboard(int dashboardId) {
        String token = login();
        HttpHeaders headers = authHeaders(token);

        try {
            ResponseEntity<SupersetApiDtos.DashboardDetailResponse> response = restTemplate.exchange(
                    supersetBaseUrl + "/api/v1/dashboard/" + dashboardId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    SupersetApiDtos.DashboardDetailResponse.class
            );

            var body = response.getBody();
            if (body != null) {
                return body.getResult();
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch dashboard {}: {}", dashboardId, e.getMessage());
        }
        return null;
    }

    /**
     * Generates a guest token for embedded dashboard access locally.
     * Uses HMAC-SHA256 JWT with Superset's secret — no API call needed.
     * The token includes RLS (row-level security) clause filtering by band_id.
     *
     * @param dashboardId the Superset dashboard ID (integer)
     * @param bandId the band ID to filter data by (RLS)
     * @param bandName the band name for display
     * @return JWT guest token string
     */
    public String generateGuestToken(int dashboardId, Long bandId, String bandName) {
        try {
            Key signingKey = new SecretKeySpec(
                    guestTokenJwtSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );

            long nowSeconds = System.currentTimeMillis() / 1000;

            // Build JWT with Superset-compatible guest token format
            String jwt = Jwts.builder()
                    .setSubject("band_" + bandId)
                    .claim("user", Map.of("username", "band_" + bandId))
                    .claim("resources", List.of(Map.of(
                            "type", "dashboard",
                            "id", String.valueOf(dashboardId)
                    )))
                    .claim("rls_rules", List.of(Map.of(
                            "clause", "band_id = " + bandId
                    )))
                    .claim("iat", nowSeconds)
                    .claim("exp", nowSeconds + 3600)  // 1 hour
                    .claim("aud", supersetBaseUrl)
                    .claim("type", "guest")
                    .signWith(signingKey, SignatureAlgorithm.HS256)
                    .compact();

            log.info("Generated local guest token for dashboard id={} band {}", dashboardId, bandId);
            return jwt;
        } catch (Exception e) {
            log.error("Failed to generate guest token for dashboard id={} band {}: {}",
                    dashboardId, bandId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Registers a dashboard for embedded access in Superset.
     * If already registered, returns the existing embedded UUID.
     *
     * @param dashboardId the Superset dashboard ID (integer)
     * @param allowedDomains comma-separated list of allowed referrer domains
     * @return the embedded dashboard UUID, or null on failure
     */
    public String registerEmbeddedDashboard(int dashboardId, String allowedDomains) {
        String token = login();
        HttpHeaders headers = authHeaders(token);

        try {
            ResponseEntity<SupersetApiDtos.EmbeddedDashboardResponse> response = restTemplate.exchange(
                    supersetBaseUrl + "/api/v1/dashboard/" + dashboardId + "/embedded",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of("allowed_domains", allowedDomains), headers),
                    SupersetApiDtos.EmbeddedDashboardResponse.class
            );

            var body = response.getBody();
            if (body != null && body.getResult() != null) {
                String embeddedUuid = body.getResult().getUuid();
                log.info("Registered embedded dashboard {} -> uuid={}", dashboardId, embeddedUuid);
                return embeddedUuid;
            }
        } catch (RestClientException e) {
            log.error("Failed to register embedded dashboard {}: {}", dashboardId, e.getMessage());
        }
        return null;
    }

    /**
     * Fetches CSRF token from Superset. Required for POST endpoints in Superset 4.1.1.
     */
    private String fetchCsrfToken(String accessToken) {
        try {
            HttpHeaders headers = authHeaders(accessToken);
            ResponseEntity<SupersetApiDtos.CsrfTokenResponse> response = restTemplate.exchange(
                    supersetBaseUrl + "/api/v1/security/csrf_token/",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    SupersetApiDtos.CsrfTokenResponse.class
            );
            if (response.getBody() != null) {
                return response.getBody().getResult();
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch CSRF token: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Checks if Superset is reachable and credentials are valid.
     */
    public boolean isAvailable() {
        try {
            return login() != null;
        } catch (Exception e) {
            log.warn("Superset not available: {}", e.getMessage());
            return false;
        }
    }

    // --- Private helpers ---

    @PostConstruct
    public void init() {
        // Initial token fetch
        refreshKeycloakToken();
    }

    @Scheduled(fixedDelay = 50 * 60 * 1000) // Refresh every 50 minutes
    public void refreshKeycloakToken() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            // Use client_credentials grant (service account) to obtain a Superset-scoped token from Keycloak
            String body = "grant_type=client_credentials" +
                    "&client_id=" + keycloakSupersetClientId +
                    "&client_secret=" + keycloakSupersetClientSecret;

            ResponseEntity<java.util.Map> response = restTemplate.exchange(
                    keycloakUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    java.util.Map.class
            );

            var respBody = response.getBody();
            if (respBody != null && respBody.get("access_token") != null) {
                this.keycloakAccessToken = (String) respBody.get("access_token");
                log.info("Refreshed Superset API token via Keycloak");
            }
        } catch (Exception e) {
            log.warn("Failed to refresh Keycloak token: {}", e.getMessage());
        }
    }

    private volatile String keycloakAccessToken = null;

    /**
     * Returns the current access token, preferring Keycloak bearer if available.
     */
    private String login() {
        // Prefer Keycloak token if available
        if (keycloakAccessToken != null && !keycloakAccessToken.isEmpty()) {
            return keycloakAccessToken;
        }
        // Fallback to DB-based login
        SupersetApiDtos.LoginRequest loginRequest = new SupersetApiDtos.LoginRequest(supersetUsername, supersetPassword);

        ResponseEntity<SupersetApiDtos.LoginResponse> response = restTemplate.exchange(
                supersetBaseUrl + "/api/v1/security/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest, loginHeaders()),
                SupersetApiDtos.LoginResponse.class
        );

        var loginBody = response.getBody();
        if (loginBody != null && loginBody.getToken() != null) {
            return loginBody.getToken();
        }
        throw new RuntimeException("Superset login failed: no token in response");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        return headers;
    }

    private HttpHeaders loginHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        return headers;
    }
}
