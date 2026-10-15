package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.michalbzowski.windband.application.query.attention.AttentionItemCollector;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;
import pl.michalbzowski.windband.config.TestSecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the broken production self-registration entry:
 * {@code GET /register} used to 302 to a URL with a doubled leading scheme
 * (https://https://login...) whenever {@code KEYCLOAK_PUBLIC_URL} already
 * contained the scheme, stranding unauthenticated users on an unreachable host.
 *
 * <p>The property value is loaded from {@code self-registration/prod-doubled-scheme.properties}
 * to reproduce the exact production property shape.</p>
 */
@WebMvcTest(PageController.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:self-registration/prod-doubled-scheme.properties")
@Import(TestSecurityConfig.class)
class SelfRegistrationRedirectTest {

    @Autowired
    private MockMvc mvc;

    // TestSecurityConfig#testUserDetailsService calls encoder.encode(...) — a
    // Mockito mock would return null, so provide a real encoder.
    @TestConfiguration
    static class TestBeans {

        @org.springframework.context.annotation.Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    // Mock every service PageController injects via @RequiredArgsConstructor.
    @MockBean
    private MemberQueryService memberQueryService;

    @MockBean
    private RehearsalQueryService rehearsalQueryService;

    @MockBean
    private EventQueryService eventQueryService;

    @MockBean
    private UnifiedInventoryItemQueryService inventoryQueryService;

    @MockBean
    private BandQueryService bandQueryService;

    @MockBean
    private TeamQueryService teamQueryService;

    @MockBean
    private MemberAttributeQueryService attributeQueryService;

    @MockBean
    private AttentionItemCollector attentionItemCollector;

    /**
     * Unauthenticated /register must redirect to the Keycloak registration URL
     * with exactly ONE leading scheme, whatever the raw property holds.
     */
    @Test
    void unauthenticatedRegister_redirectsToSingleSchemeKeycloakUrl() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://login.bandmanager.pl/realms/windband/"
                        + "protocol/openid-connect/registrations?client_id=windband-manager"));
    }
}
