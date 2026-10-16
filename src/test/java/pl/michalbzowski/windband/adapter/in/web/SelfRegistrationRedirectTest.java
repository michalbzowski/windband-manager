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
 * Regression test for the self-registration entry {@code GET /register}.
 *
 * <p>Historically the page 302-ed to a hand-assembled Keycloak
 * {@code .../openid-connect/registrations} URL whose {@code redirect_uri}
 * pointed back at {@code /register} itself. Keycloak created the account and
 * returned the authorization code to {@code /register}, which never consumes
 * codes — the user was stranded in a redirect loop seeing an error even
 * though the account already existed (and a retry failed because the e-mail
 * was already taken).</p>
 *
 * <p>The fixed flow delegates to Spring's native OAuth2 entry point so the
 * authorization request (and its state) lives in the session and the code
 * lands on {@code /login/oauth2/code/keycloak}.</p>
 */
@WebMvcTest(PageController.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:self-registration/test-env.properties")
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
     * Unauthenticated /register must hand off to Spring's OAuth2 filter so the
     * flow (state included) is owned by the client — never a raw Keycloak URL.
     */
    @Test
    void anonymousUserIsSentThroughNativeOAuth2Flow() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/keycloak"));
    }

}
