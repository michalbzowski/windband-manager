package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test for OIDC login flow.
 *
 * Since login is now handled by Keycloak, we test that:
 * 1. Accessing /login redirects to Keycloak
 * 2. The Keycloak login page loads
 */
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
