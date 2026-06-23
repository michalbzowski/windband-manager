package pl.michalbzowski.windband.infrastructure.superset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Client for Superset REST API.
 * Handles authentication (login → access token → API calls) and dashboard operations.
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

    public SupersetClient(
            RestTemplate restTemplate,
            @Value("${superset.base-url:http://localhost:8088}") String supersetBaseUrl,
            @Value("${superset.username:admin}") String supersetUsername,
            @Value("${superset.password:admin}") String supersetPassword) {
        this.restTemplate = restTemplate;
        this.supersetBaseUrl = supersetBaseUrl.replaceAll("/$", "");
        this.supersetUsername = supersetUsername;
        this.supersetPassword = supersetPassword;
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

        if (response.getBody() != null && response.getBody().getResult() != null) {
            return response.getBody().getResult();
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

            if (response.getBody() != null) {
                return response.getBody().getResult();
            }
        } catch (RestClientException e) {
            log.warn("Failed to fetch dashboard {}: {}", dashboardId, e.getMessage());
        }
        return null;
    }

    /**
     * Generates a guest token for embedded dashboard access.
     * The token includes RLS (row-level security) clause filtering by band_id.
     *
     * @param dashboardUuid the Superset dashboard UUID (required by embedded SDK)
     * @param bandId the band ID to filter data by (RLS)
     * @param bandName the band name for display
     * @return JWT guest token string
     */
    public String generateGuestToken(int dashboardId, Long bandId, String bandName) {
        String token = login();
        HttpHeaders headers = authHeaders(token);

        // Build guest token request with RLS
        SupersetApiDtos.GuestTokenRequest request = new SupersetApiDtos.GuestTokenRequest();

        // User context
        SupersetApiDtos.GuestTokenRequest.User user = new SupersetApiDtos.GuestTokenRequest.User();
        user.setUsername("band_" + bandId);
        request.setUser(user);

        // Resource (dashboard) — use integer ID for guest token API
        SupersetApiDtos.GuestTokenRequest.Resource resource = new SupersetApiDtos.GuestTokenRequest.Resource();
        resource.setType("dashboard");
        resource.setId(String.valueOf(dashboardId));
        request.setResources(List.of(resource));

        // RLS rule: filter by band_id
        SupersetApiDtos.GuestTokenRequest.RlsRule rlsRule = new SupersetApiDtos.GuestTokenRequest.RlsRule();
        rlsRule.setClause("band_id = " + bandId);
        request.setRls(List.of(rlsRule));

        try {
            ResponseEntity<SupersetApiDtos.GuestTokenResponse> response = restTemplate.exchange(
                    supersetBaseUrl + "/api/v1/security/guest_token/",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    SupersetApiDtos.GuestTokenResponse.class
            );

            if (response.getBody() != null && response.getBody().getToken() != null) {
                log.info("Generated guest token for dashboard id={} band {}", dashboardId, bandId);
                return response.getBody().getToken();
            }
        } catch (RestClientException e) {
            log.error("Failed to generate guest token for dashboard id={} band {}: {}",
                    dashboardId, bandId, e.getMessage());
        }
        return null;
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

            if (response.getBody() != null && response.getBody().getResult() != null) {
                String embeddedUuid = response.getBody().getResult().getUuid();
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

    /**
     * Logs in to Superset and returns the access token.
     */
    private String login() {
        SupersetApiDtos.LoginRequest loginRequest = new SupersetApiDtos.LoginRequest(supersetUsername, supersetPassword);

        ResponseEntity<SupersetApiDtos.LoginResponse> response = restTemplate.exchange(
                supersetBaseUrl + "/api/v1/security/login",
                HttpMethod.POST,
                new HttpEntity<>(loginRequest, loginHeaders()),
                SupersetApiDtos.LoginResponse.class
        );

        if (response.getBody() != null && response.getBody().getToken() != null) {
            return response.getBody().getToken();
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
