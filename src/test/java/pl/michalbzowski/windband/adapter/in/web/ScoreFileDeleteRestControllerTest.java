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

import java.util.List;

import pl.michalbzowski.windband.adapter.in.security.ActiveTeamResolver;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.composition.ScoreFileDeleteCommandService;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-2.5 — wires a standalone MockMvc for {@link ScoreFileDeleteRestController} +
 * {@link GlobalExceptionHandler} to pin the HTTP-level contract of
 * {@code DELETE /bands/{bandId}/compositions/{compositionId}/files/{fileId}} without
 * booting a full Spring context: 204 on success, 403 on cross-team access (pentest P1),
 * 409 for the inner cross-band / unknown-file consistency paths.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDeleteRestControllerTest {

    private static final long USER_ID = 7L;
    private static final long MY_TEAM = 1L;

    @Mock
    private TeamQueryService teamQueryService;

    @Mock
    private OidcUser oidcDelegate;

    private MockMvc mvc;
    private ScoreFileDeleteCommandService service;

    @BeforeEach
    void setUp() {
        service = org.mockito.Mockito.mock(ScoreFileDeleteCommandService.class);
        var controller = new ScoreFileDeleteRestController(service, new ActiveTeamResolver(teamQueryService));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation
                                .AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsTeam(long teamId) {
        WindbandOidcUser wu = new WindbandOidcUser(oidcDelegate, USER_ID, "michal", "michal@example.com",
                true, false, teamId, "sokole", "ADMIN", List.of(MY_TEAM));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(wu, null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    @Test
    @DisplayName("DELETE on an owned score file → 204 No Content")
    void deleteOwnFile_noContent() throws Exception {
        authenticateAsTeam(MY_TEAM);

        mvc.perform(delete("/bands/1/compositions/100/files/42"))
                .andExpect(status().isNoContent());

        verify(service).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Pentest P1: plik z obcego bandu (99 ≠ active team 1) → 403, serwis nie jest wzywany")
    void deleteForeignBand_forbiddenNoServiceCall() throws Exception {
        authenticateAsTeam(MY_TEAM);

        mvc.perform(delete("/bands/99/compositions/100/files/42"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Brak dostępu do zasobów tego zespołu")));

        verify(service, never()).delete(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (cross-band) → 409 from the global handler")
    void deleteCrossBand_conflict() throws Exception {
        authenticateAsTeam(MY_TEAM);
        doThrow(new IllegalStateException("Score file 7 does not belong to band 1"))
                .when(service).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/bands/1/compositions/100/files/7"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (unknown file) → 409 from the global handler")
    void deleteUnknownFile_conflict() throws Exception {
        authenticateAsTeam(MY_TEAM);
        doThrow(new IllegalStateException("Score file 404 does not exist."))
                .when(service).delete(anyLong(), anyLong(), anyLong());

        mvc.perform(delete("/bands/1/compositions/100/files/404"))
                .andExpect(status().isConflict());
    }
}
