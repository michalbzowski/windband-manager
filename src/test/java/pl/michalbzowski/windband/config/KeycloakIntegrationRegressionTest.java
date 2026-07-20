package pl.michalbzowski.windband.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for Keycloak integration.
 * Tests that:
 * 1. /login redirects to Keycloak OIDC authorization endpoint
 * 2. /register redirects to Keycloak registration page
 * 3. Protected endpoints require authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KeycloakIntegrationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Disabled("Requires Keycloak/stubbed OAuth2 server — not available in CI test environment")
    @Test
    void loginRedirectsToKeycloak() throws Exception {
        // /login should redirect to Keycloak OIDC authorization
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void registerRedirectsToKeycloak() throws Exception {
        // /register (when not authenticated) should redirect
        mockMvc.perform(get("/register"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void publicApiEndpointsAreAccessible() throws Exception {
        // API endpoints that don't require auth
        mockMvc.perform(get("/api/auth/check-username?username=test"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        // Protected endpoints should require authentication
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().is3xxRedirection());
    }
}
