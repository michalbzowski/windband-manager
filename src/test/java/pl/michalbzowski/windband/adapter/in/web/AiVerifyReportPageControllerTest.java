package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.query.scoreanalysis.AiVerifyReportDto;
import pl.michalbzowski.windband.application.query.scoreanalysis.AiVerifyReportQueryService;
import pl.michalbzowski.windband.application.query.scoreanalysis.ProposalsPreviewDto;

/**
 * Flagged feature M1 — view controller: flag off → 404 (invisible surface), band guard,
 * empty-report state and model wiring for the rendered template.
 */
class AiVerifyReportPageControllerTest {

    private static final long BAND = 1L;
    private static final long COMPOSITION = 7L;

    private final AiVerifyReportQueryService reportQueryService = mock(AiVerifyReportQueryService.class);
    private final WindbandOidcUser member = mock(WindbandOidcUser.class);
    private final WindbandOidcUser outsider = mock(WindbandOidcUser.class);

    AiVerifyReportPageController enabled() {
        return new AiVerifyReportPageController(reportQueryService, true);
    }

    @Test
    @DisplayName("flaga OFF → 404 nawet dla członka zespołu (endpoint niewidoczny)")
    void flagOffAnswers404() {
        when(member.belongsToTeam(BAND)).thenReturn(true);
        AiVerifyReportPageController disabled = new AiVerifyReportPageController(reportQueryService, false);

        ResponseStatusException expected = null;
        try {
            disabled.verifyAi(BAND, COMPOSITION, member, mock(Model.class));
        } catch (ResponseStatusException ex) {
            expected = ex;
        }
        assertThat(expected).isNotNull();
        assertThat(expected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("flaga ON + użytkownik spoza zespołu → odmowa (ISE → 409 wg GlobalExceptionHandler)")
    void bandGuardDeniesOutsider() {
        when(outsider.belongsToTeam(BAND)).thenReturn(false);

        assertThatThrownBy(() -> enabled().verifyAi(BAND, COMPOSITION, outsider, mock(Model.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dostępu");
    }

    @Test
    @DisplayName("flaga ON + bez zakończonej analizy → widok stanu pustego (report = null)")
    void emptyReportStillRendersView() {
        when(member.belongsToTeam(BAND)).thenReturn(true);
        when(reportQueryService.reportForLatestSucceeded(COMPOSITION, BAND)).thenReturn(Optional.empty());

        Model model = mock(Model.class);
        String view = enabled().verifyAi(BAND, COMPOSITION, member, model);

        assertThat(view).isEqualTo("compositions/verify-ai");
        verify(model).addAttribute("report", null);
        verify(model).addAttribute("bandId", BAND);
        verify(model).addAttribute("compositionId", COMPOSITION);
    }

    @Test
    @DisplayName("flaga ON + gotowa analiza → payload raportu trafia do modelu")
    void reportPayloadLandsInModel() {
        when(member.belongsToTeam(BAND)).thenReturn(true);
        AiVerifyReportDto report = new AiVerifyReportDto(
                42L, 9L, 10,
                new ProposalsPreviewDto.WorkMeta("Zadzwonił Polonez", "J. Elsner", null),
                List.of(new ProposalsPreviewDto.InstrumentOption(13L, "Trąbka")),
                false, 1, 2,
                List.of(),
                List.of(),
                List.of());
        when(reportQueryService.reportForLatestSucceeded(COMPOSITION, BAND)).thenReturn(Optional.of(report));

        Model model = mock(Model.class);
        String view = enabled().verifyAi(BAND, COMPOSITION, member, model);

        assertThat(view).isEqualTo("compositions/verify-ai");
        verify(model).addAttribute("report", report);
        verify(model).addAttribute("bandId", BAND);
        verify(model).addAttribute("compositionId", COMPOSITION);
    }
}
