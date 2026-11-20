package pl.michalbzowski.windband.application.query.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * T2 — raport spójności: preview (bez zmian) + werdykty spójności na wierszach + pageCount z pliku.
 * Wzorzec fikstury zdziedziczony po {@code ScoreAnalysisProposalsQueryServiceTest}.
 */
class AiVerifyReportQueryServiceTest {

    private static final Long BAND_ID = 1L;
    private static final Long COMPOSITION_ID = 5L;
    private static final Long ANALYSIS_ID = 9L;
    private static final Long FILE_ID = 77L;

    @TempDir
    Path workDir;

    private final Composition composition = mock(Composition.class);
    private final Band band = mock(Band.class);
    private final CompositionRepository compositionRepository = mock(CompositionRepository.class);
    private final ScoreAnalysisRepository analysisRepository = mock(ScoreAnalysisRepository.class);
    private final InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final InstrumentRoleMapRepository roleMapRepository = mock(InstrumentRoleMapRepository.class);
    private final ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);
    private final CompositionInstrumentRepository partRepository = mock(CompositionInstrumentRepository.class);

    private final Instrument trabka = mock(Instrument.class);

    private AiVerifyReportQueryService service;

    @BeforeEach
    void setUp() throws Exception {
        when(composition.getId()).thenReturn(COMPOSITION_ID);
        when(composition.getBand()).thenReturn(band);
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));

        when(trabka.getId()).thenReturn(10L);
        when(trabka.getName()).thenReturn("Trąbka");
        when(instrumentRepository.findAllOrderBySortPriorityByBandId(BAND_ID)).thenReturn(List.of(trabka));

        InstrumentRoleMap mapRow = mock(InstrumentRoleMap.class);
        when(mapRow.getSourceTag()).thenReturn("Trąbka");
        when(mapRow.getTargetRolePattern()).thenReturn("Trąbka II");
        when(roleMapRepository.findAllByBandId(BAND_ID)).thenReturn(List.of(mapRow));

        when(memberRepository.findAllActiveByBandId(BAND_ID)).thenReturn(List.of());

        // Trzy głosy: Trąbka (zmapowana) 1–3, Puzon 3–5 i Tuba 10–40 — nakładanie, luka i
        // przekroczenie pageCount (36) w jednym zestawie.
        Files.writeString(workDir.resolve("arrangement.json"),
                "[{\"role\":\"Trąbka II\",\"pageFrom\":1,\"pageTo\":3,\"confidence\":0.9},"
                        + "{\"role\":\"Puzon\",\"pageFrom\":3,\"pageTo\":5,\"confidence\":0.8},"
                        + "{\"role\":\"Tuba\",\"pageFrom\":10,\"pageTo\":40,\"confidence\":0.7}]");

        ScoreAnalysis analysis = withId(succeeded(workDir, FILE_ID));
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));
        when(analysisRepository.findLatestByCompositionIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(analysis));

        ScoreFile scoreFile = mock(ScoreFile.class);
        when(scoreFile.getId()).thenReturn(FILE_ID);
        when(scoreFile.getPageCount()).thenReturn(36);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(scoreFile));

        RoleInstrumentResolver resolver =
                new RoleInstrumentResolver(instrumentRepository, memberRepository, roleMapRepository);
        ScoreAnalysisProposalsQueryService previewQuery = new ScoreAnalysisProposalsQueryService(
                compositionRepository, analysisRepository, instrumentRepository, resolver);
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of());
        service = new AiVerifyReportQueryService(previewQuery, analysisRepository, scoreFileRepository, partRepository);
    }

    private ScoreAnalysis succeeded(Path dir, long scoreFileId) {
        return ScoreAnalysis.pending(composition, scoreFileId)
                .markRunning("job-1")
                .succeed(
                        dir.resolve("arrangement.json").toString(),
                        dir.resolve("x.musicxml").toString(),
                        dir.resolve("x.mid").toString(),
                        dir.resolve("validation.txt").toString());
    }

    /** Emulates JPA state: an entity loaded from the repository always carries its id. */
    private ScoreAnalysis withId(ScoreAnalysis analysis) {
        ScoreAnalysis spy = org.mockito.Mockito.spy(analysis);
        when(spy.getId()).thenReturn(ANALYSIS_ID);
        return spy;
    }

    @Test
    @DisplayName("Werdykty: nakładanie na sąsiadach, luka i przekroczenie — jeden wiersz liczony raz")
    void reportCarriesPerRowVerdictsAndPageFacts() {
        AiVerifyReportDto dto = service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.rowCount()).isEqualTo(3);
        assertThat(dto.errorRows()).isEqualTo(3);
        assertThat(dto.warningRows()).isZero();
        assertThat(dto.allClear()).isFalse();
        assertThat(dto.pageCount()).isEqualTo(36);
        assertThat(dto.fileId()).isEqualTo(FILE_ID);

        assertThat(dto.rows().get(0).issues())
                .extracting(ProposalConsistency.Issue::code).containsExactly("OVERLAP");
        assertThat(dto.rows().get(0).issues().get(0).detail()).contains("Puzon").contains("3–5");
        assertThat(dto.rows().get(1).issues()).extracting(ProposalConsistency.Issue::code)
                .containsExactlyInAnyOrder("OVERLAP", "UNMAPPED");
        assertThat(dto.rows().get(2).issues()).extracting(ProposalConsistency.Issue::code)
                .containsExactlyInAnyOrder("BEYOND_PAGES", "GAP", "UNMAPPED");
    }

    @Test
    @DisplayName("pageCount = null (plik nie-PDF) — reguła beyond-pages pominięta, raport w pełni czysty")
    void nullPageCountSkipsBeyondPagesRule() throws Exception {
        Files.writeString(workDir.resolve("arrangement.json"),
                "[{\"role\":\"Trąbka II\",\"pageFrom\":1,\"pageTo\":99,\"confidence\":0.9}]");
        ScoreAnalysis analysis = succeeded(workDir, FILE_ID);
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));

        ScoreFile scoreFile = mock(ScoreFile.class);
        when(scoreFile.getId()).thenReturn(FILE_ID);
        when(scoreFile.getPageCount()).thenReturn(null);
        when(scoreFileRepository.findById(FILE_ID)).thenReturn(Optional.of(scoreFile));

        AiVerifyReportDto dto = service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.pageCount()).isNull();
        assertThat(dto.allClear()).isTrue();
        assertThat(dto.rows().get(0).issues()).isEmpty();
    }

    @Test
    @DisplayName("Najnowsza SUCCEEDED → raport gotowy")
    void latestSucceededReturnsReport() {
        Optional<AiVerifyReportDto> dto = service.reportForLatestSucceeded(COMPOSITION_ID, BAND_ID);

        assertThat(dto).isPresent();
        assertThat(dto.get().analysisId()).isEqualTo(ANALYSIS_ID);
        assertThat(dto.get().rowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Najnowsza analiza PENDING → raport pusty (nie błędny)")
    void latestPendingReturnsEmpty() {
        ScoreAnalysis pending = ScoreAnalysis.pending(composition, FILE_ID);
        when(analysisRepository.findLatestByCompositionIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(pending));

        assertThat(service.reportForLatestSucceeded(COMPOSITION_ID, BAND_ID)).isEmpty();
    }

    @Test
    @DisplayName("Najnowsza SUCCEEDED z nieczytelnym artefaktem → raport pusty")
    void latestWithMissingArtifactReturnsEmpty() {
        Path missing = workDir.resolve("nieistniejacy");
        ScoreAnalysis broken = withId(succeeded(missing, FILE_ID));
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(broken));
        when(analysisRepository.findLatestByCompositionIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(broken));

        assertThat(service.reportForLatestSucceeded(COMPOSITION_ID, BAND_ID)).isEmpty();
    }

    @Test
    @DisplayName("Odczyt jawnego artefaktu nieistniejącego → IllegalStateException (409)")
    void explicitReportWithMissingArtifactThrows() {
        when(analysisRepository.findById(ANALYSIS_ID))
                .thenReturn(Optional.of(succeeded(workDir.resolve("nieistniejacy"), FILE_ID)));

        assertThatThrownBy(() -> service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Utwór spoza zespołu → IllegalStateException (izolacja bandu dziedziczona z preview)")
    void crossBandCompositionIsRejected() {
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy");
    }

@Test
    @DisplayName("existingPartRanges — partie powiązane z plikiem nut (luka 14 po 1–13)")
    void existingPartRangesComesFromPartsBoundToScoreFile() {
        CompositionInstrument scorePart = mock(CompositionInstrument.class);
        when(scorePart.getPageFrom()).thenReturn(1);
        when(scorePart.getPageTo()).thenReturn(13);
        ScoreFile bound = mock(ScoreFile.class);
        when(bound.getId()).thenReturn(FILE_ID);
        when(scorePart.getScoreFile()).thenReturn(bound);

        CompositionInstrument otherFilePart = mock(CompositionInstrument.class);
        when(otherFilePart.getPageFrom()).thenReturn(4);
        when(otherFilePart.getPageTo()).thenReturn(6);
        when(otherFilePart.getScoreFile()).thenReturn(null);

        when(partRepository.findAllByComposition(composition)).thenReturn(List.of(scorePart, otherFilePart));

        AiVerifyReportDto dto = service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.partRanges()).containsExactly(new AiVerifyReportDto.PageRange(1, 13));
                        assertThat(dto.existingPartRangesJson())
                .startsWith("[")
                .endsWith("]")
                .contains(":13")
                .doesNotContain(":4")
;
    }

    @Test
    @DisplayName("Brak partii powiązanych z plikiem → fallback na wszystkie partie utworu")
    void existingPartRangesFallbackToAllPartsWhenUnbound() {
        CompositionInstrument legacy = mock(CompositionInstrument.class);
        when(legacy.getPageFrom()).thenReturn(2);
        when(legacy.getPageTo()).thenReturn(9);
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of(legacy));

        AiVerifyReportDto dto = service.report(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.partRanges()).containsExactly(new AiVerifyReportDto.PageRange(2, 9));
    }

    @Test
    @DisplayName("Null id → IllegalArgumentException (400)")
    void nullIdsAreRejected() {
        assertThatThrownBy(() -> service.report(null, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reportForLatestSucceeded(COMPOSITION_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
