package pl.michalbzowski.windband.application.command.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.command.composition.ScoreFileCommandService;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

/**
 * Faza D — "new from PDF": start = draft + file + analysis in one transaction;
 * confirm reuses the SAME acceptance kernel as the detail page.
 */
class NewFromPdfCommandServiceTest {

    private static final Long BAND_ID = 1L;
    private static final Long COMPOSITION_ID = 5L;
    private static final Long ANALYSIS_ID = 9L;
    private static final Long USER_ID = 42L;
    private static final byte[] BYTES = "PDF-bytes".getBytes();

    private final BandRepository bandRepository = mock(BandRepository.class);
    private final CompositionRepository compositionRepository = mock(CompositionRepository.class);
    private final ScoreFileCommandService scoreFileUpload = mock(ScoreFileCommandService.class);
    private final ScoreAnalysisCommandService analysisStart = mock(ScoreAnalysisCommandService.class);
    private final ScoreAnalysisRepository analysisRepository = mock(ScoreAnalysisRepository.class);
    private final LlmApiKeyQueryService llmKeyQueryService = mock(LlmApiKeyQueryService.class);
    private final AcceptAiProposalsCommandService acceptService = mock(AcceptAiProposalsCommandService.class);

    private final Band band = mock(Band.class);
    private NewFromPdfCommandService service;

    @BeforeEach
    void setUp() {
        when(band.getId()).thenReturn(BAND_ID);
        when(bandRepository.findById(BAND_ID)).thenReturn(Optional.of(band));
        when(compositionRepository.save(any(Composition.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new NewFromPdfCommandService(
                bandRepository, compositionRepository, scoreFileUpload, analysisStart,
                analysisRepository, llmKeyQueryService, acceptService);
    }

    private static ScoreFileDto storedFile(long fileId) {
        return new ScoreFileDto(fileId, null, "nut.pdf", BYTES.length, "application/pdf",
                4, false, "sha", null, Instant.now());
    }

    // --- start --------------------------------------------------------------

    @Test
    @DisplayName("start: szkic + zapis PDF + analiza; tytuł z nazwy pliku przy pustym polu")
    void startCreatesDraftFileAndAnalysis() {
        when(scoreFileUpload.upload(any(), any(), any())).thenReturn(storedFile(77L));
        when(llmKeyQueryService.plainKey(USER_ID, LlmProvider.OPENROUTER)).thenReturn(Optional.of("sk-test"));
        when(analysisStart.start(77L, BAND_ID, "sk-test")).thenReturn(ANALYSIS_ID);

        NewFromPdfCommandService.StartResult result = service.start(
                BYTES, "application/pdf", "Zadzwonił Polonez.pdf", null, BAND_ID, USER_ID);

        assertThat(result.fileId()).isEqualTo(77L);
        assertThat(result.analysisId()).isEqualTo(ANALYSIS_ID);

        org.mockito.ArgumentCaptor<Composition> compCaptor = org.mockito.ArgumentCaptor.forClass(Composition.class);
        verify(compositionRepository).save(compCaptor.capture());
        assertThat(compCaptor.getValue().getTitle()).isEqualTo("Zadzwonił Polonez"); // fallback z nazwy
        verify(scoreFileUpload).upload(any(), any(), any());
    }

    @Test
    @DisplayName("start: pusty plik → IAE")
    void startRejectsEmptyBytes() {
        assertThatThrownBy(() -> service.start(new byte[0], "application/pdf", "x.pdf", null, BAND_ID, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("treści");
    }

    @Test
    @DisplayName("start: nie-PDF → IAE (400)")
    void startRejectsNonPdf() {
        assertThatThrownBy(() -> service.start(BYTES, "text/plain", "x.txt", null, BAND_ID, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("start: brak zespołu → ISE (409)")
    void startRejectsUnknownBand() {
        when(bandRepository.findById(BAND_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(BYTES, "application/pdf", "x.pdf", null, BAND_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie znaleziony");
    }

    // --- confirm ------------------------------------------------------------

    @Test
    @DisplayName("confirm: tytuły + zaakceptowane wiersze idą przez jądro akceptacji; 303 danych")
    void confirmAppliesTextsAndAcceptsRows() {
        Composition composition = mock(Composition.class);
        when(composition.getId()).thenReturn(COMPOSITION_ID);
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));

        ScoreAnalysis succeeded = mock(ScoreAnalysis.class);
        when(succeeded.getId()).thenReturn(ANALYSIS_ID);
        when(succeeded.getPhase()).thenReturn(ScoreAnalysis.Phase.SUCCEEDED);
        when(analysisRepository.findFirstByCompositionIdOrderByIdDesc(COMPOSITION_ID))
                .thenReturn(Optional.of(succeeded));
        when(acceptService.accept(eq(COMPOSITION_ID), eq(BAND_ID), eq(ANALYSIS_ID), any(), eq("42")))
                .thenReturn(new AcceptAiProposalsCommandService.AcceptResult(2, List.of()));

        NewFromPdfCommandService.ConfirmResult result = service.confirm(
                COMPOSITION_ID, BAND_ID, "Nowy tytuł", null, null,
                List.of(new AcceptAiProposalsCommandService.AcceptedRow(
                        "Trąbka II", 3, 3, 13L, 0.7)),
                "42");

        assertThat(result.compositionId()).isEqualTo(COMPOSITION_ID);
        assertThat(result.partsCreated()).isEqualTo(2);
    }

    @Test
    @DisplayName("confirm: pusty tytuł → IAE (400)")
    void confirmRejectsBlankTitle() {
        assertThatThrownBy(() -> service.confirm(COMPOSITION_ID, BAND_ID, "  ", null, null, null, "42"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tytuł");
    }

    @Test
    @DisplayName("confirm: wiersze bez ukończonej analizy → ISE (409), akceptacja NIE idzie w świat")
    void confirmRowsWithoutSucceededAnalysis() {
        Composition composition = mock(Composition.class);
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));
        when(analysisRepository.findFirstByCompositionIdOrderByIdDesc(COMPOSITION_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
                COMPOSITION_ID, BAND_ID, "Tytuł", null, null,
                List.of(new AcceptAiProposalsCommandService.AcceptedRow(
                        "Trąbka II", 3, 3, 13L, 0.7)),
                "42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pomyślnie zakończona");
        verify(acceptService, never()).accept(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirm bez wierszy = tylko metadane (akceptacja pomijana)")
    void confirmWithoutRowsSkipsAcceptance() {
        Composition composition = mock(Composition.class);
        when(composition.getId()).thenReturn(COMPOSITION_ID);
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));

        NewFromPdfCommandService.ConfirmResult result = service.confirm(
                COMPOSITION_ID, BAND_ID, "Tytuł", null, "A. Arranger", null, "42");

        assertThat(result.partsCreated()).isZero();
        verify(acceptService, never()).accept(any(), any(), any(), any(), any());
    }
}
