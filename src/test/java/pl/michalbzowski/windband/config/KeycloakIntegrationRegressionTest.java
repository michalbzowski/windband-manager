package pl.michalbzowski.windband.config;

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
 * 1. Public endpoints remain accessible without authentication
 * 2. Protected endpoints require authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class KeycloakIntegrationRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpointsAreAccessibleWithoutAuth() throws Exception {
        // Public endpoints should be accessible without authentication
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        // Protected endpoints should require authentication
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isUnauthorized());
    }
}