package pl.michalbzowski.windband.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.scoreanalysis.AcceptAiProposalsCommandService;
import pl.michalbzowski.windband.application.query.scoreanalysis.AiProposalPreview;
import pl.michalbzowski.windband.application.query.scoreanalysis.ProposalsPreviewDto;
import pl.michalbzowski.windband.application.query.scoreanalysis.ScoreAnalysisProposalsQueryService;

/**
 * Faza D — the two AI acceptance endpoints: JSON contract + US-4.5 verifier extraction.
 */
class ScoreAnalysisAiRestControllerTest {

    private static final long BAND = 1L;
    private static final long COMPOSITION = 5L;
    private static final long ANALYSIS = 9L;

    private final ScoreAnalysisProposalsQueryService queryService = mock(ScoreAnalysisProposalsQueryService.class);
    private final AcceptAiProposalsCommandService commandService = mock(AcceptAiProposalsCommandService.class);
    private final WindbandOidcUser user = mock(WindbandOidcUser.class);
    private ScoreAnalysisAiRestController controller;

    @BeforeEach
    void setUp() {
        when(user.getUserId()).thenReturn(42L);
        // Flaga M1 włączona — część testów celowo buduje kontroler z flagą wyłączoną.
        controller = new ScoreAnalysisAiRestController(queryService, commandService, true);
    }

    @Test
    @DisplayName("GET /proposals → 200 JSON z rolą, stronami i metadanymi utworu")
    void proposalsReturnsJson() throws Exception {
        AiProposalPreview row = new AiProposalPreview(
                "Trąbka II", 3, 3, 0.7d, "Trąbka", 13L, "Trąbka", List.of("Jan Kowalski"));
        ProposalsPreviewDto dto = new ProposalsPreviewDto(
                new ProposalsPreviewDto.WorkMeta("Zadzwonił Polonez", "J. Elsner", null),
                List.of(new ProposalsPreviewDto.InstrumentOption(13L, "Trąbka")),
                List.of(row),
                false);
        when(queryService.preview(COMPOSITION, BAND, ANALYSIS)).thenReturn(dto);

        var response = controller.proposals(BAND, COMPOSITION, ANALYSIS);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString())
                .startsWith("application/json");
        String body = response.getBody();
        assertThat(body).contains("Trąbka II").contains("Zadzwonił Polonez")
                .contains("\"pageFrom\":3")
                .contains("\"simulated\":false");
    }

    @Test
    @DisplayName("POST /accept → weryfikator z tożsamości zalogowanego użytkownika (US-4.5)")
    void acceptForwardsUserVerifier() throws Exception {
        AcceptAiProposalsCommandService.AcceptedRow row =
                new AcceptAiProposalsCommandService.AcceptedRow("Trąbka II", 3, 3, 13L, 0.7d);
        when(commandService.accept(COMPOSITION, BAND, ANALYSIS, List.of(row), "42"))
                .thenReturn(new AcceptAiProposalsCommandService.AcceptResult(1, List.of()));

        var response = controller.accept(BAND, COMPOSITION, ANALYSIS, new ScoreAnalysisAiRestController.AcceptBody(
                List.of(row)), user);

        ArgumentCaptor<List> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandService).accept(org.mockito.ArgumentMatchers.eq(COMPOSITION),
                org.mockito.ArgumentMatchers.eq(BAND), org.mockito.ArgumentMatchers.eq(ANALYSIS),
                rowsCaptor.capture(), org.mockito.ArgumentMatchers.eq("42"));
        assertThat(rowsCaptor.getValue()).hasSize(1);

        assertThat(response.getBody()).contains("\"created\":1").contains("\"skipped\":[]");
    }

    @Test
    @DisplayName("POST /accept bez zalogowanego użytkownika → stabilny znacznik systemowy")
    void acceptFallsBackToSystemVerifier() throws Exception {
        when(commandService.accept(COMPOSITION, BAND, ANALYSIS, List.of(), "ai-accept"))
                .thenThrow(new IllegalArgumentException("Lista akceptowanych wierszy jest pusta."));

        try {
            controller.accept(BAND, COMPOSITION, ANALYSIS,
                    new ScoreAnalysisAiRestController.AcceptBody(List.of()), null);
        } catch (IllegalArgumentException expected) {
            // kontrakt service: pusta lista → IAE (tutaj ważna tylko tożsamość weryfikatora)
        }
        verify(commandService).accept(COMPOSITION, BAND, ANALYSIS, List.of(), "ai-accept");
    }


    @Test
    @DisplayName("M1: accept-corrected przekazuje wiersze + weryfikatora i zwraca wynik service")
    void acceptCorrectedForwardsRowsWithVerifier() throws java.io.IOException {
        List<AcceptAiProposalsCommandService.AcceptedRowCorrected> rows = List.of(
                new AcceptAiProposalsCommandService.AcceptedRowCorrected("Trąbka II", 3, 3, 4, 6, 13L));
        AcceptAiProposalsCommandService.AcceptCorrectedResult result =
                new AcceptAiProposalsCommandService.AcceptCorrectedResult(
                        1, 0, 1, List.of(),
                        List.of(new AcceptAiProposalsCommandService.SupersededPart(3, 4)));
        when(commandService.acceptCorrected(COMPOSITION, BAND, ANALYSIS, rows, "42"))
                .thenReturn(result);

        ResponseEntity<String> response = controller.acceptCorrected(
                BAND, COMPOSITION, ANALYSIS,
                new ScoreAnalysisAiRestController.AcceptCorrectedBody(rows), user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"created\":1").contains("\"correctedRows\":1");
        /* kontrakt zużywany przez verify-ai.html (odsłanianie luki po korekcie własnej partii) */
        assertThat(response.getBody())
                .contains("\"superseded\":[{\"pageFrom\":3,\"pageTo\":4}]");
        verify(commandService).acceptCorrected(COMPOSITION, BAND, ANALYSIS, rows, "42");
    }

    @Test
    @DisplayName("M1: flaga wyłącza accept-corrected → 404 (endpoint niewidoczny), service NIE wywołane")
    void flagOffMakesCorrectedEndpointInvisible() throws java.io.IOException {
        ScoreAnalysisAiRestController disabled =
                new ScoreAnalysisAiRestController(queryService, commandService, false);
        List<AcceptAiProposalsCommandService.AcceptedRowCorrected> rows = List.of(
                new AcceptAiProposalsCommandService.AcceptedRowCorrected("Trąbka II", 3, 3, 4, 6, 13L));

        ResponseEntity<String> response = disabled.acceptCorrected(
                BAND, COMPOSITION, ANALYSIS,
                new ScoreAnalysisAiRestController.AcceptCorrectedBody(rows), user);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(commandService, never()).acceptCorrected(anyLong(), anyLong(), anyLong(), anyList(), anyString());
    }

    @Test
    @DisplayName("M1: flaga wyłączona nie rusza starego flow (GET /proposals dalej 200)")
    void flagOffLeavesLegacyEndpointsUntouched() throws java.io.IOException {
        ScoreAnalysisAiRestController disabled =
                new ScoreAnalysisAiRestController(queryService, commandService, false);
        when(queryService.preview(COMPOSITION, BAND, ANALYSIS)).thenReturn(new ProposalsPreviewDto(
                new ProposalsPreviewDto.WorkMeta("Zadzwonił Polonez", "J. Elsner", null),
                List.of(), List.of(), false));

        ResponseEntity<String> response =
                disabled.proposals(BAND, COMPOSITION, ANALYSIS);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // ─────────────── POST /gaps/assign — karty luk (2026-11-verify-ai-cardflow) ───────────────

    @Test
    @DisplayName("Karta luki: POST /gaps/assign forwarduje zakres + instrument, zwraca wynik service")
    void gapsAssignForwardsRangeAndInstrument() throws java.io.IOException {
        AcceptAiProposalsCommandService.AcceptCorrectedResult result =
                new AcceptAiProposalsCommandService.AcceptCorrectedResult(1, 0, 1, List.of(), List.of());
        when(commandService.assignGapPages(COMPOSITION, BAND, ANALYSIS, 8, 9, 12L, "42"))
                .thenReturn(result);

        ResponseEntity<String> response = controller.gapsAssign(
                BAND, COMPOSITION, ANALYSIS,
                new ScoreAnalysisAiRestController.GapAssignBody(8, 9, 12L), user);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"created\":1");
        verify(commandService).assignGapPages(COMPOSITION, BAND, ANALYSIS, 8, 9, 12L, "42");
    }

    @Test
    @DisplayName("Karta luki: pusta treść żądania → IAE (400 przez handler), service nie wywołane")
    void gapsAssignRejectsEmptyBody() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.gapsAssign(
                BAND, COMPOSITION, ANALYSIS, null, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("treści żądania");
        verify(commandService, never()).assignGapPages(anyLong(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                anyLong(), anyString());
    }

    @Test
    @DisplayName("Karta luki: flaga wyłączona → 404 (endpoint niewidoczny), service nie wywołane")
    void flagOffHidesGapsEndpoint() throws java.io.IOException {
        ScoreAnalysisAiRestController disabled =
                new ScoreAnalysisAiRestController(queryService, commandService, false);

        ResponseEntity<String> response = disabled.gapsAssign(
                BAND, COMPOSITION, ANALYSIS,
                new ScoreAnalysisAiRestController.GapAssignBody(8, 9, 12L), user);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(commandService, never()).assignGapPages(anyLong(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                anyLong(), anyString());
    }
}