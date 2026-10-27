package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.adapter.in.security.ActiveTeamResolver;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.member.CrossTeamAccessDeniedException;
import pl.michalbzowski.windband.application.command.member.MemberCommandService;
import pl.michalbzowski.windband.application.dto.MemberDto;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pentest P0 (A01/BOLA) /api/members: regression suite for per-id endpoints.
 *
 * <p>Nadzoruje, czy każdy mutujący/odczytowy endpoint po id faktycznie przechodzi
 * przez bramkę przynależności do AKTYWNEGO zespołu żądającego (a nie tylko
 * uwierzytelnienia) — i czy odmowa nie wzywa już komend (brak efektów ubocznych).
 *
 * <p>Uwaga: stan faktyczny "członek → band" jest nadzorowany osobno w
 * integration testach ({@code MemberQueryServiceTest}); tutaj — kontrakt HTTP i wiring.
 */
@ExtendWith(MockitoExtension.class)
class MemberControllerTest {

    private static final long USER_ID = 7L;
    private static final long MY_TEAM = 1L;
    private static final long FOREIGN_TEAM = 2L;

    @Mock
    private MemberCommandService commandService;

    @Mock
    private MemberQueryService queryService;

    @Mock
    private TeamQueryService teamQueryService;

    @Mock
    private OidcUser oidcDelegate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // Prawdziwy resolver (testowana logika sesji + tożsamości), mockowany reposit layer.
        var controller = new MemberController(commandService, queryService,
                new ActiveTeamResolver(teamQueryService));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                // Standalone setup does not register Spring Security's argument resolvers.
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private WindbandOidcUser principal(Long activeTeamId) {
        return new WindbandOidcUser(oidcDelegate, USER_ID, "michal", "michal@example.com",
                true, false, activeTeamId, "sokole", "ADMIN", List.of(MY_TEAM));
    }

    private void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private static MemberDto sampleMember() {
        return new MemberDto(21L, "Anna", "Kowalska", LocalDate.of(1995, 3, 10), 31,
                false, false, "anna@example.com", null, true, "trąbka", List.of("trąbka"),
                LocalDate.of(2020, 1, 1), null, 1L, "trąbka", true);
    }

    // ============ GET /api/members/{id} ============

    @Test
    @DisplayName("GET /api/members/21 dla członka własnego teamu → 200 (gate pasuje)")
    void getMember_ownTeam_ok() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(21L, MY_TEAM)).thenReturn(sampleMember());

        mvc.perform(get("/api/members/21").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21))
                .andExpect(jsonPath("$.firstName").value("Anna"));

        verify(queryService).requireMemberOfTeam(21L, MY_TEAM);
    }

    @Test
    @DisplayName("GET /api/members/99 (obcy team) → 403 z jednolita odpowiedzia")
    void getMember_foreignTeam_forbidden() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(99L, MY_TEAM))
                .thenThrow(new CrossTeamAccessDeniedException(99L, MY_TEAM));

        mvc.perform(get("/api/members/99"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Brak dostępu do zasobów tego zespołu")));
    }

    @Test
    @DisplayName("GET /api/members/21 z atrybutem sesji innego teamu (z przynależnościa) → 200 w jego zakresie")
    void getMember_sessionTeamAttribute_winsOverPrincipal() throws Exception {
        authenticateAs(principal(MY_TEAM));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("activeTeamId", FOREIGN_TEAM);
        when(teamQueryService.getUserTeam(USER_ID, FOREIGN_TEAM))
                .thenReturn(Optional.of(new TeamQueryService.UserTeamDto(FOREIGN_TEAM, "Echo", "echo", "MEMBER")));
        when(queryService.requireMemberOfTeam(21L, FOREIGN_TEAM)).thenReturn(sampleMember());

        mvc.perform(get("/api/members/21").session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anna"));

        verify(queryService).requireMemberOfTeam(21L, FOREIGN_TEAM);
    }

    @Test
    @DisplayName("GET /api/members/21 z atrybutem sesji teamu, z którego użytkownika usunięto → fallback do teamu z tożsamości")
    void getMember_staleSessionAttribute_fallsBackToPrincipalTeam() throws Exception {
        authenticateAs(principal(MY_TEAM));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("activeTeamId", 99L);
        when(teamQueryService.getUserTeam(USER_ID, 99L)).thenReturn(Optional.empty());
        when(queryService.requireMemberOfTeam(21L, MY_TEAM)).thenReturn(sampleMember());

        mvc.perform(get("/api/members/21").session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Anna"));
    }

    @Test
    @DisplayName("GET /api/members/21 bez aktywnego zespołu → 403")
    void getMember_noActiveTeam_forbidden() throws Exception {
        authenticateAs(principal(null));

        mvc.perform(get("/api/members/21"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Brak aktywnego zespołu w bieżącej sesji")));
    }

    @Test
    @DisplayName("GET /api/members/21 z tożsamościa spoza windbandu → 403")
    void getMember_foreignPrincipal_forbidden() throws Exception {
        authenticateAs(oidcDelegate); // surowy OidcUser, nie WindbandOidcUser

        mvc.perform(get("/api/members/21"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Brak aktywnego zespołu w bieżącej sesji")));

        verify(queryService, never()).requireMemberOfTeam(anyLong(), anyLong());
    }

    // ============ PUT /api/members/{id} ============

    @Test
    @DisplayName("PUT /api/members/99 (obcy team) → 403 i komenda update NIE jest wywołana")
    void updateMember_foreignTeam_forbidden_noCommand() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(99L, MY_TEAM))
                .thenThrow(new CrossTeamAccessDeniedException(99L, MY_TEAM));

        mvc.perform(put("/api/members/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"X\",\"lastName\":\"Y\"}"))
                .andExpect(status().isForbidden());

        verify(commandService, never()).updateMember(any(), any());
    }

    // ============ DELETE /api/members/{id} ============

    @Test
    @DisplayName("DELETE /api/members/99 (obcy team) → 403 i komenda deactivate NIE jest wywołana")
    void deactivateForeignTeam_forbidden_noCommand() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(99L, MY_TEAM))
                .thenThrow(new CrossTeamAccessDeniedException(99L, MY_TEAM));

        mvc.perform(delete("/api/members/99"))
                .andExpect(status().isForbidden());

        verify(commandService, never()).deactivateMember(anyLong());
    }

    @Test
    @DisplayName("DELETE /api/members/21 (własny team) → 204 + komenda wywołana")
    void deactivateOwnTeam_ok() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(21L, MY_TEAM)).thenReturn(sampleMember());

        mvc.perform(delete("/api/members/21"))
                .andExpect(status().isNoContent());

        verify(commandService).deactivateMember(21L);
    }

    // ============ POST /api/members/{id}/instruments ============

    @Test
    @DisplayName("POST /api/members/99/instruments (obcy team) → 403 bez wywołania komendy")
    void assignInstrument_foreignTeam_forbidden_noCommand() throws Exception {
        authenticateAs(principal(MY_TEAM));
        when(queryService.requireMemberOfTeam(99L, MY_TEAM))
                .thenThrow(new CrossTeamAccessDeniedException(99L, MY_TEAM));

        mvc.perform(post("/api/members/99/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentName\":\"trąbka\",\"primary\":true}"))
                .andExpect(status().isForbidden());

        verify(commandService, never()).assignInstrument(any());
    }
}
