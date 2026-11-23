package pl.michalbzowski.windband.application.command.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusion;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusionRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

/**
 * Strona M1.5 — oznaczanie stron „nie wykorzystam”: walidacje zakresu i kolizji
 * z istniejącymi partiami, scalanie dotykających znaczeń oraz odnawianie (restauracja)
 * w całości i częściowe (split znaku na dwa odcinki).
 */
class ExcludePagesCommandServiceTest {

    private static final Long BAND_ID = 1L;
    private static final Long OTHER_BAND_ID = 99L;
    private static final Long COMPOSITION_ID = 5L;
    private static final Long SCORE_FILE_ID = 77L;
    private static final Long ANALYSIS_ID = 9L;
    private static final String MARKED_BY = "42";

    private final Band band = mock(Band.class);
    private final Composition composition = mock(Composition.class);
    private final ScoreFile scoreFile = mock(ScoreFile.class);
    private final Instrument trubka = mock(Instrument.class);
    private final ScorePageExclusionRepository exclusionRepository = mock(ScorePageExclusionRepository.class);
    private final CompositionInstrumentRepository partRepository = mock(CompositionInstrumentRepository.class);
    private final CompositionRepository compositionRepository = mock(CompositionRepository.class);
    private final ScoreAnalysisRepository analysisRepository = mock(ScoreAnalysisRepository.class);
    private final ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);

    private ExcludePagesCommandService service;

    @BeforeEach
    void setUp() {
        when(band.getId()).thenReturn(BAND_ID);
        when(composition.getId()).thenReturn(COMPOSITION_ID);
        when(composition.getBand()).thenReturn(band);
        when(scoreFile.getId()).thenReturn(SCORE_FILE_ID);
        when(scoreFile.getComposition()).thenReturn(composition);
        when(scoreFile.getPageCount()).thenReturn(10);

        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));
        when(scoreFileRepository.findById(SCORE_FILE_ID)).thenReturn(Optional.of(scoreFile));

        ScoreAnalysis analysis = ScoreAnalysis.pending(composition, SCORE_FILE_ID)
                .markRunning("job-1")
                .succeed("/tmp/arrangement.json", "m", "m2", "v");
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));

        InstrumentRepository instrumentRepo = mock(InstrumentRepository.class);
        when(instrumentRepo.findByIdAndBandId(13L, BAND_ID)).thenReturn(Optional.of(trubka));
        when(trubka.getId()).thenReturn(13L);
        when(trubka.getName()).thenReturn("Trąbka");

        when(exclusionRepository.findAllByScoreFile(scoreFile)).thenReturn(List.of());
        when(exclusionRepository.save(any(ScorePageExclusion.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new ExcludePagesCommandService(exclusionRepository, partRepository,
                compositionRepository, analysisRepository, scoreFileRepository);
    }

    private static CompositionInstrument partOn(int pageFrom, int pageTo,
                                                Composition composition, ScoreFile scoreFile) {
        CompositionInstrument part = mock(CompositionInstrument.class);
        when(part.getComposition()).thenReturn(composition);
        when(part.getScoreFile()).thenReturn(scoreFile);
        when(part.getPageFrom()).thenReturn(pageFrom);
        when(part.getPageTo()).thenReturn(pageTo);
        return part;
    }

    private static ScorePageExclusion markOf(int pageFrom, int pageTo,
                                             Composition composition, ScoreFile scoreFile) {
        ScorePageExclusion exclusion = mock(ScorePageExclusion.class);
        when(exclusion.getId()).thenReturn((long) pageFrom * 100 + pageTo);
        when(exclusion.getComposition()).thenReturn(composition);
        when(exclusion.getScoreFile()).thenReturn(scoreFile);
        when(exclusion.getPageFrom()).thenReturn(pageFrom);
        when(exclusion.getPageTo()).thenReturn(pageTo);
        when(exclusion.getMarkedBy()).thenReturn("1");
        when(exclusion.getMarkedAt()).thenReturn(java.time.Instant.parse("2026-11-01T08:00:00Z"));
        return exclusion;
    }

    @Test
    @DisplayName("zakres odwrócony → IAE (400)")
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> service.markUnused(composition, scoreFile, 5, 2, MARKED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("odwrócony");
        verify(exclusionRepository, never()).save(any());
    }

    @Test
    @DisplayName("zakres wykraczający poza plik nut → IAE (400)")
    void rangeBeyondPageCountIsRejected() {
        assertThatThrownBy(() -> service.markUnused(composition, scoreFile, 9, 12, MARKED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("poza plik");
    }

    @Test
    @DisplayName("strony już objęte istniejącą partią → IAE (400) — głos pozostaje źródłem prawdy")
    void pagesCoveredByStoredPartAreRejected() {
        CompositionInstrument taken = partOn(4, 5, composition, scoreFile);
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of(taken));

        assertThatThrownBy(() -> service.markUnused(composition, scoreFile, 4, 6, MARKED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("już w głosie");
    }

    @Test
    @DisplayName("partia innego pliku nut nie blokuje zakresu tego pliku")
    void partsOfOtherScoreFilesDoNotBlock() {
        ScoreFile otherFile = mock(ScoreFile.class);
        CompositionInstrument foreignPart = partOn(4, 5, composition, otherFile);
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of(foreignPart));

        ExcludedScope saved = service.markUnused(composition, scoreFile, 4, 6, MARKED_BY);

        assertThat(saved.pageFrom()).isEqualTo(4);
        assertThat(saved.pageTo()).isEqualTo(6);
    }

    @Test
    @DisplayName("poprawne oznaczenie zapisuje znaczenie z autorem i momentem")
    void validRangeMarkIsStoredWithAuditing() {
        ExcludedScope saved = service.markUnused(composition, scoreFile, 4, 6, MARKED_BY);

        assertThat(saved.pageFrom()).isEqualTo(4);
        assertThat(saved.pageTo()).isEqualTo(6);

        ArgumentCaptor<ScorePageExclusion> captor = ArgumentCaptor.forClass(ScorePageExclusion.class);
        verify(exclusionRepository).save(captor.capture());
        ScorePageExclusion entity = captor.getValue();
        assertThat(entity.getComposition()).isEqualTo(composition);
        assertThat(entity.getScoreFile()).isEqualTo(scoreFile);
        assertThat(entity.getPageFrom()).isEqualTo(4);
        assertThat(entity.getPageTo()).isEqualTo(6);
        assertThat(entity.getMarkedBy()).isEqualTo(MARKED_BY);
        assertThat(entity.getMarkedAt()).isNotNull();
    }

    @Test
    @DisplayName("dotykające oznaczenia scalają się, a starsze znaczenie zostaje zdjęte")
    void touchingMarksMergeAndOlderIsDeleted() {
        ScorePageExclusion older = markOf(1, 3, composition, scoreFile);
        when(exclusionRepository.findAllByScoreFile(scoreFile)).thenReturn(List.of(older));

        ExcludedScope saved = service.markUnused(composition, scoreFile, 4, 6, MARKED_BY);

        assertThat(saved.pageFrom()).isEqualTo(1);
        assertThat(saved.pageTo()).isEqualTo(6);
        verify(exclusionRepository).delete(older);
        verify(exclusionRepository).save(any(ScorePageExclusion.class));
    }

    @Test
    @DisplayName("przywrócenie dokładnego zakresu usuwa znak całkowicie")
    void restoreOfExactRangeDeletesTheMark() {
        ScorePageExclusion mark = markOf(4, 6, composition, scoreFile);
        when(exclusionRepository.findAllByScoreFile(scoreFile)).thenReturn(List.of(mark));

        List<ExcludedScope> restored = service.removeOverlap(composition, scoreFile, 4, 6);

        assertThat(restored).hasSize(1);
        assertThat(restored.get(0).pageFrom()).isEqualTo(4);
        assertThat(restored.get(0).pageTo()).isEqualTo(6);
        verify(exclusionRepository).delete(mark);
        verify(exclusionRepository, never()).save(any());
    }

    @Test
    @DisplayName("przywrócenie częściowe dzieli znak na odcinek lewy i prawy")
    void partialRestoreSplitsTheMark() {
        ScorePageExclusion mark = markOf(1, 6, composition, scoreFile);
        when(exclusionRepository.findAllByScoreFile(scoreFile)).thenReturn(List.of(mark));

        List<ExcludedScope> restored = service.removeOverlap(composition, scoreFile, 3, 4);

        // Zwracany przekrój = to, co faktycznie uwalniamy (3–4); ogony 1–2 i 5–6 zostają z audytem oryginału.
        assertThat(restored)
                .extracting(ExcludedScope::pageFrom, ExcludedScope::pageTo)
                .containsExactly(org.assertj.core.api.Assertions.tuple(3, 4));

        ArgumentCaptor<ScorePageExclusion> captor = ArgumentCaptor.forClass(ScorePageExclusion.class);
        verify(exclusionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ScorePageExclusion::getPageFrom, ScorePageExclusion::getPageTo)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(1, 2),
                        org.assertj.core.api.Assertions.tuple(5, 6));
        verify(exclusionRepository).delete(mark);
    }

    @Test
    @DisplayName("przywrócenie bez przecięcia nie zmienia stanu znaczeń")
    void restoreWithoutOverlapIsNoop() {
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of());

        List<ExcludedScope> restored = service.removeOverlap(composition, scoreFile, 6, 8);

        assertThat(restored).isEmpty();
        verify(exclusionRepository, never()).save(any());
        verify(exclusionRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("odnawianie przez REST: utwór spoza zespołu → ISE (409)")
    void crossBandCompositionIsRejectedOnRestPath() {
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, OTHER_BAND_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markUnusedByIds(OTHER_BAND_ID, COMPOSITION_ID,
                ANALYSIS_ID, 2, 3, MARKED_BY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy do zespołu");
    }
}
