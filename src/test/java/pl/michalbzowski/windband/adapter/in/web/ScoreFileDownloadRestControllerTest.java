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

import java.io.ByteArrayInputStream;
import java.util.List;

import pl.michalbzowski.windband.adapter.in.security.ActiveTeamResolver;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadQueryService;
import pl.michalbzowski.windband.application.query.composition.ScoreFileDownloadMetadata;
import pl.michalbzowski.windband.application.query.team.TeamQueryService;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-2.4 — wires the standalone MockMvc for {@link ScoreFileDownloadRestController} + {@link GlobalExceptionHandler}
 * so we can assert the HTTP-level contract without starting a full Spring context: Content-Type,
 * Content-Disposition (inline vs. attachment), and status codes for the 403/409/410 paths.
 *
 * <p>Pentest P1 (A01/BOLA): band w sciezce /bands/{bandId} musi sypac z aktywnym zespołem
 * ządającego — inaczej 403 przed jakimkolwiek kontaktem z warstwą serwisowa.
 */
@ExtendWith(MockitoExtension.class)
class ScoreFileDownloadRestControllerTest {

    private static final long USER_ID = 7L;
    private static final long MY_TEAM = 1L;

    @Mock
    private TeamQueryService teamQueryService;

    @Mock
    private OidcUser oidcDelegate;

    private MockMvc mvc;
    private ScoreFileDownloadQueryService service;

    @BeforeEach
    void setUp() {
        service = mock(ScoreFileDownloadQueryService.class);
        var controller = new ScoreFileDownloadRestController(service, new ActiveTeamResolver(teamQueryService));
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

    private ScoreFileDownloadQueryService.Download handle(byte[] bytes, String name, String mime, boolean zip) {
        var meta = new ScoreFileDownloadMetadata(42L, name, mime, (long) bytes.length, zip);
        return new ScoreFileDownloadQueryService.Download(new ByteArrayInputStream(bytes), meta);
    }

    @Test
    @DisplayName("PDF download → 200 + Content-Disposition inline;filename=\"score.pdf\" + body bytes")
    void downloadPdf_inline() throws Exception {
        authenticateAsTeam(MY_TEAM);
        byte[] body = "PDF-BYTES".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.open(anyLong(), anyLong(), anyLong())).thenAnswer(inv -> handle(body, "score.pdf", "application/pdf", false));

        mvc.perform(get("/bands/1/compositions/100/files/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"score.pdf\""))
                .andExpect(content().bytes(body));
    }

    @Test
    @DisplayName("ZIP download → 200 + Content-Disposition attachment;filename=\"parts.zip\"")
    void downloadZip_attachment() throws Exception {
        authenticateAsTeam(MY_TEAM);
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenAnswer(inv -> handle(new byte[] { 0, 1 }, "parts.zip", "application/zip", true));

        mvc.perform(get("/bands/1/compositions/100/files/42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"parts.zip\""));
    }

    @Test
    @DisplayName("Pentest P1: plik z obcego bandu (99 ≠ active team 1) → 403, serwis nie jest wzywany")
    void downloadForeignBand_forbiddenNoServiceCall() throws Exception {
        authenticateAsTeam(MY_TEAM);

        mvc.perform(get("/bands/99/compositions/100/files/42"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Brak dostępu do zasobów tego zespołu")));

        verify(service, never()).open(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("brak aktywnego zespołu → 403, serwis nie jest wzywany")
    void downloadNoActiveTeam_forbidden() throws Exception {
        WindbandOidcUser noTeam = new WindbandOidcUser(oidcDelegate, USER_ID, "michal", "michal@example.com",
                true, false, null, null, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(noTeam, null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));

        mvc.perform(get("/bands/1/compositions/100/files/42"))
                .andExpect(status().isForbidden());

        verify(service, never()).open(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("Service throws IllegalStateException (cross-band) → 409 from the global handler")
    void downloadCrossBand_conflict() throws Exception {
        authenticateAsTeam(MY_TEAM);
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("ScoreFile 7 does not belong to band 1"));

        mvc.perform(get("/bands/1/compositions/100/files/7")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Service throws ScoreFileMissingException (path missing) → 410 from the global handler")
    void downloadMissingOnDisk_gone() throws Exception {
        authenticateAsTeam(MY_TEAM);
        when(service.open(anyLong(), anyLong(), anyLong()))
                .thenThrow(new ScoreFileDownloadQueryService.ScoreFileMissingException(
                        "Nie udało się odczytać pliku 45 z dysku."));

        mvc.perform(get("/bands/1/compositions/100/files/45")).andExpect(status().isGone());
    }
}
