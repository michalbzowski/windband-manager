package pl.michalbzowski.windband.application.command.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.application.query.scoreanalysis.RoleInstrumentResolver;

/**
 * Faza D / US-4.5 — acceptance of AI proposal rows: verified parts, AI vs HYBRID sourcing,
 * range fidelity, no overwrite of existing roles, band isolation.
 */
class AcceptAiProposalsCommandServiceTest {

    private static final Long BAND_ID = 1L;
    private static final Long COMPOSITION_ID = 5L;
    private static final Long ANALYSIS_ID = 9L;
    private static final Long SCORE_FILE_ID = 77L;
    private static final String VERIFIER = "42";

    @TempDir
    Path workDir;

    private final Band band = mock(Band.class);
    private final Composition composition = mock(Composition.class);
    private final ScoreFile scoreFile = mock(ScoreFile.class);
    private final CompositionRepository compositionRepository = mock(CompositionRepository.class);
    private final ScoreAnalysisRepository analysisRepository = mock(ScoreAnalysisRepository.class);
    private final ScoreFileRepository scoreFileRepository = mock(ScoreFileRepository.class);
    private final InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
    private final CompositionInstrumentRepository partRepository = mock(CompositionInstrumentRepository.class);
    private final InstrumentRepository bandInstruments = mock(InstrumentRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final InstrumentRoleMapRepository roleMapRepository = mock(InstrumentRoleMapRepository.class);

    private AcceptAiProposalsCommandService service;

    private final Instrument flet = mock(Instrument.class);
    private final Instrument trabka = mock(Instrument.class);
    private final Instrument kornet = mock(Instrument.class);

    @BeforeEach
    void setUp() throws Exception {
        when(band.getId()).thenReturn(BAND_ID);
        when(composition.getId()).thenReturn(COMPOSITION_ID);
        when(composition.getBand()).thenReturn(band);
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, BAND_ID))
                .thenReturn(Optional.of(composition));

        when(trabka.getId()).thenReturn(13L);
        when(trabka.getName()).thenReturn("Trąbka");
        when(kornet.getId()).thenReturn(14L);
        when(kornet.getName()).thenReturn("Kornet");
        when(kornet.getAliasOf()).thenReturn(trabka);
        when(flet.getId()).thenReturn(12L);
        when(flet.getName()).thenReturn("Flet");
        for (Instrument i : List.of(flet, trabka, kornet)) {
            when(i.getBand()).thenReturn(band);
        }
        when(bandInstruments.findAllOrderBySortPriorityByBandId(BAND_ID))
                .thenReturn(List.of(flet, trabka, kornet));
        InstrumentRoleMap row = mock(InstrumentRoleMap.class);
        when(row.getSourceTag()).thenReturn("Trąbka");
        when(row.getTargetRolePattern()).thenReturn("Trąbka II");
        when(roleMapRepository.findAllByBandId(BAND_ID)).thenReturn(List.of(row));
        when(memberRepository.findAllActiveByBandId(BAND_ID)).thenReturn(List.of());

        RoleInstrumentResolver resolver = new RoleInstrumentResolver(
                bandInstruments, memberRepository, roleMapRepository);

        when(scoreFile.getComposition()).thenReturn(composition);
        when(scoreFile.getPageCount()).thenReturn(10);
        when(scoreFileRepository.findById(SCORE_FILE_ID)).thenReturn(Optional.of(scoreFile));

        Files.writeString(workDir.resolve("arrangement.json"), """
                [
                  {"role":"Flet I","pageFrom":1,"pageTo":1,"confidence":0.8},
                  {"role":"Trąbka II","pageFrom":3,"pageTo":3,"confidence":0.7}
                ]""");
        ScoreAnalysis analysis = ScoreAnalysis.pending(composition, SCORE_FILE_ID)
                .markRunning("job-1")
                .succeed(
                        workDir.resolve("arrangement.json").toString(),
                        "m", "m2", "v");
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));

        when(partRepository.findAllByComposition(composition)).thenReturn(List.of());
        when(partRepository.save(any(CompositionInstrument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service = new AcceptAiProposalsCommandService(
                compositionRepository, analysisRepository, scoreFileRepository,
                instrumentRepository, partRepository, resolver);
    }

    private static AcceptAiProposalsCommandService.AcceptedRow row(
            String role, int from, int to, Long instrumentId, Double confidence) {
        return new AcceptAiProposalsCommandService.AcceptedRow(role, from, to, instrumentId, confidence);
    }

    @Test
    @DisplayName("zaakceptowany wiersz → zweryfikowana partia AI powiązana z plikiem analizy")
    void acceptedRowCreatesVerifiedAiPart() {
        when(instrumentRepository.findByIdAndBandId(13L, BAND_ID)).thenReturn(Optional.of(trabka));

        AcceptAiProposalsCommandService.AcceptResult result = service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 13L, 0.7)), VERIFIER);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEmpty();

        ArgumentCaptor<CompositionInstrument> captor = ArgumentCaptor.forClass(CompositionInstrument.class);
        verify(partRepository).save(captor.capture());
        CompositionInstrument part = captor.getValue();
        assertThat(part.getSource()).isEqualTo(PartSource.AI);
        assertThat(part.getInstrumentRole()).isEqualTo("Trąbka II");
        assertThat(part.getInstrument()).isEqualTo(trabka);
        assertThat(part.getScoreFile()).isEqualTo(scoreFile);
        assertThat(part.getConfidenceScore()).isEqualTo(0.7d);
        assertThat(part.getVerifiedBy()).isEqualTo(VERIFIER); // US-4.5: akceptacja = weryfikacja
        assertThat(part.getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("zmiana instrumentu względem propozycji AI → partia HYBRID")
    void changedInstrumentCreatesHybridPart() {
        when(instrumentRepository.findByIdAndBandId(14L, BAND_ID)).thenReturn(Optional.of(kornet));

        service.accept(COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 14L, 0.7)), VERIFIER);

        ArgumentCaptor<CompositionInstrument> captor = ArgumentCaptor.forClass(CompositionInstrument.class);
        verify(partRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(PartSource.HYBRID);
    }

    @Test
    @DisplayName("rola spoza propozycji analizy → IAE (400)")
    void unknownRoleRejected() {
        assertThatThrownBy(() -> service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Klarnet B", 2, 4, 12L, 0.5)), VERIFIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie występuje");
    }

    @Test
    @DisplayName("złamany zakres stron względem propozycji → IAE (400)")
    void mismatchedRangeRejected() {
        assertThatThrownBy(() -> service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 1, 1, 13L, 0.7)), VERIFIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Zakres");
    }

    @Test
    @DisplayName("rola już w utworze → pominięcie, NIGDY nadpisanie (inaczej case roli)")
    void existingRoleSkippedNotOverwritten() {
        CompositionInstrument taken = mock(CompositionInstrument.class);
        when(taken.getInstrumentRole()).thenReturn("trąbka ii"); // celowo inny zapis
        when(partRepository.findAllByComposition(composition)).thenReturn(List.of(taken));

        AcceptAiProposalsCommandService.AcceptResult result = service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 13L, 0.7)), VERIFIER);

        assertThat(result.created()).isZero();
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).role()).isEqualTo("Trąbka II");
        verify(partRepository, never()).save(any(CompositionInstrument.class));
    }

    @Test
    @DisplayName("instrument spoza zespołu → IAE (400)")
    void foreignInstrumentRejected() {
        when(instrumentRepository.findByIdAndBandId(99L, BAND_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 99L, 0.7)), VERIFIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie należy do zespołu");
    }

    @Test
    @DisplayName("analiza nieukończona → ISE (409)")
    void unfinishedAnalysisRejected() {
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(
                Optional.of(ScoreAnalysis.pending(composition, SCORE_FILE_ID)));

        assertThatThrownBy(() -> service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 13L, 0.7)), VERIFIER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ukończona");
    }

    @Test
    @DisplayName("braki: puste wiersze / bezweryfikatora / null id → IAE")
    void guards() {
        assertThatThrownBy(() -> service.accept(COMPOSITION_ID, BAND_ID, ANALYSIS_ID, List.of(), VERIFIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pusta");
        assertThatThrownBy(() -> service.accept(
                COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 13L, null)), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tożsamości");
        assertThatThrownBy(() -> service.accept(null, BAND_ID, ANALYSIS_ID,
                List.of(row("Trąbka II", 3, 3, 13L, null)), VERIFIER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("propozycja z teamTagiem: wybór zgodny z tagiem → partia AI, nazwa głosu zachowana")
    void teamTagMapsAiSource() throws Exception {
        Files.writeString(workDir.resolve("arrangement.json"),
                "[{\"role\":\"Klarnet Basowy\",\"pageFrom\":6,\"pageTo\":6,"
                        + "\"confidence\":0.85,\"teamTag\":\"Trąbka\"}]");
        when(instrumentRepository.findByIdAndBandId(13L, BAND_ID)).thenReturn(Optional.of(trabka));

        service.accept(COMPOSITION_ID, BAND_ID, ANALYSIS_ID,
                List.of(row("Klarnet Basowy", 6, 6, 13L, 0.85)), VERIFIER);

        ArgumentCaptor<CompositionInstrument> captor = ArgumentCaptor.forClass(CompositionInstrument.class);
        verify(partRepository).save(captor.capture());
        // głos "Klarnet Basowy" sam w sobie nie jest instrumentem zespołu — AI przyznaje tag
        assertThat(captor.getValue().getSource()).isEqualTo(PartSource.AI);
        assertThat(captor.getValue().getInstrumentRole()).isEqualTo("Klarnet Basowy");
    }
}
