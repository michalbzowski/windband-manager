package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test for OIDC login flow.
 *
 * <p><strong>@Disabled:</strong> Requires Keycloak or stubbed OAuth2 authorization server
 * running. In CI, no Keycloak is available, so /login renders the local form login
 * (200 OK) instead of redirecting (302) to the OAuth2 authorization endpoint.</p>
 */
@Disabled("Requires Keycloak/stubbed OAuth2 server — not available in CI test environment")
class LoginUiTest extends UiTestBase {

    @Test
    void shouldRedirectToKeycloakLogin() {
        driver.get(baseUrl() + "/login");

        // Should redirect to Keycloak (not show local login form)
        // Keycloak login page title contains "Sign in" or "Log in" or realm name
        String currentUrl = driver.getCurrentUrl();
        // Either redirected to Keycloak or to the oauth2 authorization endpoint
        assertThat(currentUrl)
                .as("Login should redirect to Keycloak or OAuth2 authorization endpoint")
                .doesNotContain("/login");
    }
}
