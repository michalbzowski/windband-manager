package pl.michalbzowski.windband.application.query.scoreanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * US-4.2 — artefact download resolution: two-layer band isolation, latest-analysis scoping,
 * phase guard and the on-disk existence check, WITHOUT starting a Spring context (unit, Mockito).
 */
@ExtendWith(MockitoExtension.class)
class ScoreAnalysisArtifactQueryServiceTest {

    @Mock private BandQueryService bandQueryService;
    @Mock private CompositionRepository compositionRepository;
    @Mock private ScoreAnalysisRepository analysisRepository;

    private ScoreAnalysisArtifactQueryService service;

    private final Long bandId = 1L;
    private final Long compositionId = 100L;

    private Path jsonFile;

    @BeforeEach
    void setUp() throws Exception {
        service = new ScoreAnalysisArtifactQueryService(bandQueryService, compositionRepository, analysisRepository);
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "sa-artifact-unit-" + System.nanoTime());
        Files.createDirectories(root);
        jsonFile = root.resolve("arrangement.json");
        Files.writeString(jsonFile, "[{\"role\":\"Obój\"}]");
    }

    private Band newBand(Long id) {
        Band b = Mockito.mock(Band.class);
        lenient().when(b.getId()).thenReturn(id);
        return b;
    }

    private Composition newComposition(Long id, Long owningBandId) {
        // Must NOT be called inside another stub's thenReturn() (no nested stubbing).
        Band owner = newBand(owningBandId);
        Composition c = Mockito.mock(Composition.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getBand()).thenReturn(owner);
        return c;
    }

    private ScoreAnalysis newRow(String jsonPath) {
        ScoreAnalysis row = Mockito.mock(ScoreAnalysis.class);
        lenient().when(row.getId()).thenReturn(55L);
        lenient().when(row.getPhase()).thenReturn(ScoreAnalysis.Phase.SUCCEEDED);
        lenient().when(row.getArrangementJsonPath()).thenReturn(jsonPath);
        return row;
    }

    private void stubBandAndComposition() {
        Band band = newBand(bandId);
        Composition composition = newComposition(compositionId, bandId);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(band);
        when(compositionRepository.findByIdAndBandId(compositionId, bandId)).thenReturn(Optional.of(composition));
    }

    @Test
    @DisplayName("own SUCCEEDED analysis with an on-disk artefact resolves to a readable Artifact")
    void openOwnArtefact_success() {
        stubBandAndComposition();
        ScoreAnalysis row = newRow(jsonFile.toAbsolutePath().toString());
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.of(row));

        ScoreAnalysisArtifactQueryService.Artifact resolved = service.open(compositionId, bandId, "json");

        assertThat(resolved.path()).isEqualTo(jsonFile.toAbsolutePath());
        assertThat(resolved.fileName()).isEqualTo("arrangement.json");
        assertThat(resolved.mediaType().toString()).isEqualTo("application/json");
    }

    @Test
    @DisplayName("unknown artefact name → IllegalArgumentException (→ 400)")
    void openUnknownArtefact_throwsIae() {
        stubBandAndComposition();
        ScoreAnalysis row = newRow(jsonFile.toAbsolutePath().toString());
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.open(compositionId, bandId, "wav"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nieznany artefakt");
    }

    @Test
    @DisplayName("composition not found → IllegalArgumentException (→ 400)")
    void openCompositionNotFound_throwsIae() {
        Band band = newBand(bandId);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(band);
        when(compositionRepository.findByIdAndBandId(compositionId, bandId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie został znaleziony");
    }

    @Test
    @DisplayName("cross-band composition → band-scoped read is empty → IllegalArgumentException (→ 400)")
    void openCrossBand_throwsIae() {
        Band band = newBand(bandId);
        when(bandQueryService.getRequiredBand(bandId)).thenReturn(band);
        // The port refuses unscoped reads: for a foreign composition the band-scoped lookup is empty.
        when(compositionRepository.findByIdAndBandId(compositionId, bandId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie został znaleziony w zespole");
    }

    @Test
    @DisplayName("no analysis recorded → IllegalStateException (→ 409)")
    void openNoAnalysis_throwsIse() {
        stubBandAndComposition();
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Brak analizy AI");
    }

    @Test
    @DisplayName("non-successful phase → IllegalStateException (→ 409)")
    void openFailedPhase_throwsIse() {
        stubBandAndComposition();
        ScoreAnalysis row = Mockito.mock(ScoreAnalysis.class);
        lenient().when(row.getId()).thenReturn(56L);
        lenient().when(row.getPhase()).thenReturn(ScoreAnalysis.Phase.FAILED);
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("faza: FAILED");
    }

    @Test
    @DisplayName("artefact unrecorded on the row → IllegalStateException (→ 409)")
    void openUnrecorded_throwsIse() {
        stubBandAndComposition();
        ScoreAnalysis row = newRow(null);
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie został wygenerowany");
    }

    @Test
    @DisplayName("recorded path missing on disk → IllegalStateException (→ 409)")
    void openMissingOnDisk_throwsIse() {
        stubBandAndComposition();
        Path ghost = jsonFile.resolveSibling("ghost.json");
        ScoreAnalysis row = newRow(ghost.toAbsolutePath().toString());
        when(analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.open(compositionId, bandId, "json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nie istnieje na dysku");
    }
}
