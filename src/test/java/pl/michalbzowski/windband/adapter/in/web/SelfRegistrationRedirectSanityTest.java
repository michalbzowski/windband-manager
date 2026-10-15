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
 * Sanity guard: when the registration property is ALREADY clean (single scheme,
 * plain http) — the local-stack shape — the redirect must not be altered at all.
 * Protects against over-eager URL "fixing" that would force https:// onto the
 * local plain-HTTP Keycloak and break localhost dev.
 */
@WebMvcTest(PageController.class)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:self-registration/local-clean-http.properties")
@Import(TestSecurityConfig.class)
class SelfRegistrationRedirectSanityTest {

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

    @Test
    void cleanHttpProperty_isRedirectedUnchanged() throws Exception {
        mvc.perform(get("/register"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:8180/realms/windband/"
                        + "protocol/openid-connect/registrations?client_id=windband-local"));
    }
}
