package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.team.BandInventoryApiQueryService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1 inventory integration API (B10): HTTP-level contract for
 * {@code GET /api/v1/me/bands} and {@code GET /api/v1/bands/{bandId}/members},
 * including the 403 paths (foreign band / non-windband principal).
 */
@ExtendWith(MockitoExtension.class)
class BandInventoryApiControllerTest {

    private static final long USER_ID = 7L;

    @Mock
    private BandInventoryApiQueryService queryService;

    @Mock
    private OidcUser oidcDelegate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new BandInventoryApiController(queryService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // Standalone setup does not register Spring Security's argument
                // resolvers; without this, @AuthenticationPrincipal falls through
                // to ModelAttributeMethodProcessor (and fails to instantiate).
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Stands in for an authenticated windband session (realm "windband", user #7). */
    private WindbandOidcUser principal() {
        return new WindbandOidcUser(oidcDelegate, USER_ID, "michal", "michal@example.com",
                true, false, 1L, "sokole", "ADMIN", List.of(1L, 9L));
    }

    private void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    @Test
    @DisplayName("GET /api/v1/me/bands → 200 + [{bandId, name, role}]")
    void myBands_returnsMapping() throws Exception {
        authenticateAs(principal());
        when(queryService.myBands(USER_ID)).thenReturn(List.of(
                new BandInventoryApiQueryService.BandView(1L, "Sokole", "ADMIN"),
                new BandInventoryApiQueryService.BandView(9L, "Echo", "MEMBER")));

        mvc.perform(get("/api/v1/me/bands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bandId").value(1))
                .andExpect(jsonPath("$[0].name").value("Sokole"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].bandId").value(9))
                .andExpect(jsonPath("$[1].role").value("MEMBER"));
    }

    @Test
    @DisplayName("GET /api/v1/bands/{id}/members → 200 + roster with expectedSet (instrument + primary)")
    void bandMembers_returnsRosterWithExpectedSet() throws Exception {
        authenticateAs(principal());
        when(queryService.bandMembersWithExpectedSet(USER_ID, 1L)).thenReturn(List.of(
                new BandInventoryApiQueryService.MemberView(21L, "Anna", "Kowalska", true, List.of(
                        new BandInventoryApiQueryService.ExpectedEntry("trąbka", true),
                        new BandInventoryApiQueryService.ExpectedEntry("flugelhorn", false))),
                new BandInventoryApiQueryService.MemberView(22L, "Jan", "Nowak", false, List.of())));

        mvc.perform(get("/api/v1/bands/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").value(21))
                .andExpect(jsonPath("$[0].firstName").value("Anna"))
                .andExpect(jsonPath("$[0].lastName").value("Kowalska"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].expectedSet[0].instrument").value("trąbka"))
                .andExpect(jsonPath("$[0].expectedSet[0].primary").value(true))
                .andExpect(jsonPath("$[0].expectedSet[1].instrument").value("flugelhorn"))
                .andExpect(jsonPath("$[0].expectedSet[1].primary").value(false))
                .andExpect(jsonPath("$[1].memberId").value(22))
                .andExpect(jsonPath("$[1].active").value(false))
                .andExpect(jsonPath("$[1].expectedSet").isEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/bands/{id}/members for foreign band → 403 + uniform error body")
    void bandMembers_foreignBand_forbidden() throws Exception {
        authenticateAs(principal());
        when(queryService.bandMembersWithExpectedSet(USER_ID, 99L))
                .thenThrow(new BandInventoryApiQueryService.NotBandMemberException(99L));

        mvc.perform(get("/api/v1/bands/99/members"))
                .andExpect(status().isForbidden())
                // error body comes from GlobalExceptionHandler (web-layer converter
                // dependent in standalone MockMvc) — pin status + message text only.
                .andExpect(content().string(containsString("User is not a member of band 99")));
    }

    @Test
    @DisplayName("GET /api/v1/me/bands with a non-windband principal → 403 (no user mapping possible)")
    void myBands_foreignPrincipal_forbidden() throws Exception {
        authenticateAs(oidcDelegate);

        mvc.perform(get("/api/v1/me/bands"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("not authenticated as a windband user")));
    }
}
