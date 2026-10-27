package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pl.michalbzowski.windband.adapter.in.security.ActiveTeamResolver;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.report.ReportCompiler;
import pl.michalbzowski.windband.application.report.ReportGeneratorService;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pentest P0 (A01/BOLA) /jasper-report: raport musi powstawać WYŁĄCZNIE po stronie
 * serwera z aktywnym zespołem żądającego — parametr {@code band_id} z URL jest
 * ignorowany, a brak kontekstu zespołu kończy się 403 bez generowania.
 */
@ExtendWith(MockitoExtension.class)
class JasperReportControllerTest {

    private static final long USER_ID = 7L;
    private static final String REPORT_KEY = "sprawozdanie-miesieczne";
    private static final long MY_TEAM = 3L;

    @Mock
    private ReportCompiler reportCompiler;

    @Mock
    private ReportGeneratorService reportGeneratorService;

    @Mock
    private ActiveTeamResolver activeTeamResolver;

    @Mock
    private OidcUser oidcDelegate;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new JasperReportController(reportCompiler, reportGeneratorService,
                activeTeamResolver);
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

    private void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private void authenticateAsWindbandUser(long activeTeamId) {
        authenticateAs(new WindbandOidcUser(oidcDelegate, USER_ID, "michal", "michal@example.com",
                true, false, activeTeamId, "sokole", "ADMIN", List.of(activeTeamId)));
    }

    @Test
    @DisplayName("Generowanie raportu: band_id z aktywnego zespołu nadpisuje wartość klienta")
    void generate_forcesActiveTeamBandId_overridingClientValue() throws Exception {
        authenticateAsWindbandUser(MY_TEAM);
        when(activeTeamResolver.require(any(), any())).thenReturn(MY_TEAM);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        when(reportGeneratorService.generatePdf(eq(REPORT_KEY), params.capture()))
                .thenReturn(new byte[]{9, 8, 7});

        mvc.perform(get("/jasper-report/" + REPORT_KEY)
                        // Atak: klient sam dyktuje zakres danych.
                        .param("band_id", "99")
                        .param("date_from", "2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"sprawozdanie-miesieczne.pdf\""));

        Map<String, Object> forwarded = params.getValue();
        // Wartość klienta (99) musiała zostać nadpisana tożsamością serwera (3).
        org.assertj.core.api.Assertions.assertThat(forwarded.get("band_id")).isEqualTo(3);
        // Pozostałe, legalne parametry użytkownika muszą dotrzeć bez zmian.
        org.assertj.core.api.Assertions.assertThat(forwarded)
                .containsEntry("date_from", "2026-01-01");
    }

    @Test
    @DisplayName("Brak aktywnego zespołu → 403 i raport NIE jest generowany")
    void generate_withoutActiveTeam_forbidden_andNoGeneration() throws Exception {
        authenticateAsWindbandUser(0L);
        when(activeTeamResolver.require(any(), any()))
                .thenThrow(new AccessDeniedException("Brak aktywnego zespołu w bieżącej sesji"));

        mvc.perform(get("/jasper-report/" + REPORT_KEY)
                        .param("band_id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Brak aktywnego zespołu")));

        verify(reportGeneratorService, never()).generatePdf(any(), any());
    }

    @Test
    @DisplayName("Nieznany raport → 404 (zachowany kontrakt istniejącego endpointu)")
    void generate_unknownReport_notFound() throws Exception {
        authenticateAsWindbandUser(MY_TEAM);
        when(activeTeamResolver.require(any(), any())).thenReturn(MY_TEAM);
        when(reportGeneratorService.generatePdf(eq("ghost"), any(Map.class)))
                .thenThrow(new IllegalArgumentException("Raport nie znaleziony: ghost"));

        mvc.perform(get("/jasper-report/ghost"))
                .andExpect(status().isNotFound())
                // sendError(404, msg): w MockMvc komunikat ląduje w getErrorMessage().
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getErrorMessage())
                        .contains("Raport nie znaleziony"));
    }

    @Test
    @DisplayName("Tożsamość spoza windbandu → 403 bez generowania")
    void generate_foreignPrincipal_forbidden() throws Exception {
        authenticateAs(oidcDelegate); // surowy OidcUser — resolver nie ustali zespołu
        when(activeTeamResolver.require(any(), any()))
                .thenThrow(new AccessDeniedException("Brak aktywnego zespołu w bieżącej sesji"));

        mvc.perform(get("/jasper-report/" + REPORT_KEY))
                .andExpect(status().isForbidden());

        verify(reportGeneratorService, never()).generatePdf(any(), any(Map.class));
    }
}
