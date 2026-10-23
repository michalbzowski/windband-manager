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
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Faza D — preview of a finished analysis: band isolation, terminal phase required,
 * artefact parsing, work metadata best-effort, per-row resolution.
 */
class ScoreAnalysisProposalsQueryServiceTest {

    private static final Long BAND_ID = 1L;
    private static final Long COMPOSITION_ID = 5L;
    private static final Long ANALYSIS_ID = 9L;

    @TempDir
    Path workDir;

    private final Composition composition = mock(Composition.class);
    private final Band band = mock(Band.class);
    private final CompositionRepository compositionRepository = mock(CompositionRepository.class);
    private final ScoreAnalysisRepository analysisRepository = mock(ScoreAnalysisRepository.class);
    private final InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final InstrumentRoleMapRepository roleMapRepository = mock(InstrumentRoleMapRepository.class);

    private final RoleInstrumentResolver resolver =
            new RoleInstrumentResolver(instrumentRepository, memberRepository, roleMapRepository);
    private ScoreAnalysisProposalsQueryService service;

    private final Instrument trabka = mock(Instrument.class);
    private Path arrangementFile;

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

        Member member = mock(Member.class);
        when(member.getFirstName()).thenReturn("Jan");
        when(member.getLastName()).thenReturn("Kowalski");
        when(member.getAllInstruments()).thenReturn(List.of(trabka));
        when(memberRepository.findAllActiveByBandId(BAND_ID)).thenReturn(List.of(member));

        arrangementFile = workDir.resolve("arrangement.json");
        Files.writeString(arrangementFile,
                "[{\"role\":\"Trąbka II\",\"pageFrom\":1,\"pageTo\":2,\"confidence\":0.9}]");
        Files.writeString(workDir.resolve("analyzer-raw.json"),
                "{\"work\":{\"title\":\"Zadzwonił Polonez\",\"composer\":\"J. Elsner\"}}");

        ScoreAnalysis analysis = succeeded(composition, workDir);
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(analysis));

        service = new ScoreAnalysisProposalsQueryService(
                compositionRepository, analysisRepository, instrumentRepository, resolver);
    }

    /** Real entity: PENDING → RUNNING → SUCCEEDED with the fixture artefacts. */
    private static ScoreAnalysis succeeded(Composition comp, Path dir) {
        return ScoreAnalysis.pending(comp, 77L)
                .markRunning("job-1")
                .succeed(
                        dir.resolve("arrangement.json").toString(),
                        dir.resolve("x.musicxml").toString(),
                        dir.resolve("x.mid").toString(),
                        dir.resolve("validation.txt").toString());
    }

    @Test
    @DisplayName("SUCCEEDED → propozycje + work + lista instrumentów; rola rozwiązana na członków")
    void returnsResolvedPreview() {
        ProposalsPreviewDto dto = service.preview(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.proposals()).hasSize(1);
        AiProposalPreview row = dto.proposals().get(0);
        assertThat(row.role()).isEqualTo("Trąbka II");
        assertThat(row.pageFrom()).isEqualTo(1);
        assertThat(row.pageTo()).isEqualTo(2);
        assertThat(row.confidence()).isEqualTo(0.9d);
        assertThat(row.instrumentIdOrNull()).isEqualTo(10L);
        assertThat(row.instrumentNameOrNull()).isEqualTo("Trąbka");
        assertThat(row.memberNames()).containsExactly("Jan Kowalski");

        assertThat(dto.work().title()).isEqualTo("Zadzwonił Polonez");
        assertThat(dto.work().composer()).isEqualTo("J. Elsner");
        assertThat(dto.work().arranger()).isNull(); // brak pola → null, nie crash

        assertThat(dto.instruments()).containsExactly(
                new ProposalsPreviewDto.InstrumentOption(10L, "Trąbka"));
    }

    @Test
    @DisplayName("brak analyzer-raw.json → work puste, propozycje dalej wracają")
    void workMetaIsBestEffort() throws Exception {
        Files.delete(workDir.resolve("analyzer-raw.json"));

        ProposalsPreviewDto dto = service.preview(COMPOSITION_ID, BAND_ID, ANALYSIS_ID);

        assertThat(dto.work()).isEqualTo(ProposalsPreviewDto.WorkMeta.EMPTY);
        assertThat(dto.proposals()).hasSize(1);
    }

    @Test
    @DisplayName("analiza PENDING → IllegalStateException (→ 409)")
    void pendingAnalysisRejected() {
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(
                Optional.of(ScoreAnalysis.pending(composition, 77L)));

        assertThatThrownBy(() -> service.preview(COMPOSITION_ID, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ukończona");
    }

    @Test
    @DisplayName("SUCCEEDED bez pliku arrangement.json → IllegalStateException (→ 409)")
    void missingArtefactRejected() {
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(Optional.of(
                ScoreAnalysis.pending(composition, 77L)
                        .markRunning("job-1")
                        .succeed(workDir.resolve("nie-ma-takiego.json").toString(), "m", "m2", "v")));

        assertThatThrownBy(() -> service.preview(COMPOSITION_ID, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("arrangement.json");
    }

    @Test
    @DisplayName("analiza obcego utworu → IllegalStateException (→ 409)")
    void foreignCompositionRejected() {
        Composition foreignComp = mock(Composition.class);
        when(analysisRepository.findById(ANALYSIS_ID)).thenReturn(
                Optional.of(succeeded(foreignComp, workDir)));

        assertThatThrownBy(() -> service.preview(COMPOSITION_ID, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy do utworu");
    }

    @Test
    @DisplayName("utwór poza zespołem → ISE; null id → IAE")
    void bandIsolationAndNullGuards() {
        when(compositionRepository.findByIdAndBandId(COMPOSITION_ID, 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.preview(COMPOSITION_ID, 99L, ANALYSIS_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie należy do zespołu");

        assertThatThrownBy(() -> service.preview(null, BAND_ID, ANALYSIS_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.preview(COMPOSITION_ID, BAND_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
