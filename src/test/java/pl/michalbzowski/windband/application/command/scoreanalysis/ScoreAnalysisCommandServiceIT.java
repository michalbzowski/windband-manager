package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis.Phase;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-4.1 — integration test against a real Spring context + H2 (Flyway migrations applied
 * — the {@code score_analysis} table is present from V42). Exercises:
 *   - start() happy path: row PENDING → RUNNING with stub runnerRef; poll() transitions to SUCCEEDED with 4 artefact paths (real FS writes by the Stub);
 *   - cross-band isolation on a known score-file id (→ 409 IllegalStateException in the adapter);
 *   - ZIP / wrong-mime-type score file is rejected at start();
 *   - missing scoreFileId → IllegalArgumentException mapped to 400/422.
 */
class ScoreAnalysisCommandServiceIT extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private ScoreAnalysisRepository analysisRepository;
    @Autowired private ScoreAnalysisCommandService service;
    @Autowired private AiArtifactsLayout layout;

    private static final long BAND_A = 1L; // seed "Test Band" (see src/test/resources/data.sql)
    private static final long BAND_B = 2L; // seed "Other Band"
    private Composition ownInA;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ownInA = compositionRepository.save(
                pl.michalbzowski.windband.domain.composition.Composition.create(
                        "US-4.1 it", "desc", null, null,
                        bandRepository.findById(BAND_A).orElseThrow()));
        tempDir = Files.createTempDirectory("us-4-1-it-");
    }

    @Test
    @DisplayName("start() happy path: PENDING row is created, becomes RUNNING with runnerRef; poll() transitions to SUCCEEDED + writes four artefacts")
    void start_then_poll_reachesSucceeded_with_artefacts() {
        // Place a fake PDF so the stub can see a regular file.
        Path pdf = tempDir.resolve("song.pdf");
        try {
            Files.writeString(pdf, "fake-pdf-bytes");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ScoreFile saved = scoreFileRepository.save(ScoreFile.forComposition(
                ownInA, "application/pdf", "song.pdf", 13L, "sample-sha256", pdf.toString(), null));

        Long analysisId = service.start(saved.getId(), BAND_A);

        ScoreAnalysis rowP1 = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(rowP1.getPhase()).isEqualTo(Phase.RUNNING);     // start() has already polled once (stub is instant)
        assertThat(rowP1.getRunnerRef()).startsWith("stub-");       // evidence the runner was called

        // Force-resolve by calling poll() again — the stub returns SUCCEEDED on second read.
        ScoreAnalysis rowP2 = service.poll(analysisId);
        assertThat(rowP2.getPhase()).isEqualTo(Phase.SUCCEEDED);
        assertThat(rowP2.isTerminal()).isTrue();
        assertThat(rowP2.getArrangementJsonPath()).isNotBlank().endsWith("arrangement.json");
        assertThat(rowP2.getValidationTxtPath()).isNotBlank().endsWith("validation.txt");

        // The artefacts exist on disk at the paths persisted in the row.
        assertThat(Files.isRegularFile(Path.of(rowP2.getArrangementJsonPath()))).isTrue();
        assertThat(Files.isRegularFile(Path.of(rowP2.getArrangementMusicxmlPath()))).isTrue();
    }

    @Test
    @DisplayName("start() with a foreign band's score file → IllegalStateException (→ 409 in the adapter)")
    void crossBand_file_notOwnedByCaller_isRejected() throws Exception {
        Path pdf = tempDir.resolve("foreign.pdf");
        Files.writeString(pdf, "bytes");
        // Create a composition owned by band B.
        var foreignComp = compositionRepository.save(
                pl.michalbzowski.windband.domain.composition.Composition.create(
                        "US-4.1 foreign", null, null, null,
                        bandRepository.findById(BAND_B).orElseThrow()));
        ScoreFile fRow = scoreFileRepository.save(ScoreFile.forComposition(
                foreignComp, "application/pdf", "foreign.pdf", 5L, "sha", pdf.toString(), null));

        assertThatThrownBy(() -> service.start(fRow.getId(), BAND_A))
                .isInstanceOf(IllegalStateException.class);   // cross-band → 409 in the adapter
    }

    @Test
    @DisplayName("start() rejects ZIP files (US-4.1 MVP scope is PDF-only)")
    void zipFiles_areRejectedWithIllegalArgument() throws Exception {
        Path zip = tempDir.resolve("parts.zip");
        Files.writeString(zip, "zip-bytes");
        ScoreFile zRow = scoreFileRepository.save(ScoreFile.forComposition(
                ownInA, "application/zip", "parts.zip", 9L, "sha", zip.toString(), null));

        assertThatThrownBy(() -> service.start(zRow.getId(), BAND_A))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    @DisplayName("start() rejects missing scoreFileId (→ 400/422 in the adapter)")
    void nullScoreFileId_rejected() {
        assertThatThrownBy(() -> service.start(null, BAND_A))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start() with a missing composition for that band → IllegalStateException (→ 409 in the adapter)")
    void unknownCompositionId_rejected() throws Exception {
        Path pdf = tempDir.resolve("g.pdf");
        Files.writeString(pdf, "x");
        ScoreFile orphanRow = scoreFileRepository.save(ScoreFile.forComposition(
                ownInA, "application/pdf", "g.pdf", 1L, "sha", pdf.toString(), null));
        // A band id that no composition is registered against for this file.
        assertThatThrownBy(() -> service.start(orphanRow.getId(), 9999L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Stub forced-fail path: input filename contains 'failme' → poll() reaches FAILED with Polish message")
    void stub_failmeTrigger_marksPhaseFAILED() throws Exception {
        Path failme = tempDir.resolve("score-failme.pdf");
        Files.writeString(failme, "bytes");
        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownInA, "application/pdf", failme.getFileName().toString(), 5L,
                "sha", failme.toString(), null));

        Long analysisId = service.start(row.getId(), BAND_A);
        ScoreAnalysis after = service.poll(analysisId);
        assertThat(after.getPhase()).isEqualTo(Phase.FAILED);
        // The Polish message travels from the stub to the DB row via the adapter — keep it
        // stable so future UI can i18n by matching on this exact string.
        assertThat(after.getErrorMessage()).contains("Symulowany błąd pipeline AI");
    }

    @Test
    @DisplayName("Second start() on the same (composition, file) pair returns a SECOND row with its own id + runnerRef (no uniqueness constraint on (comp_id, score_file_id) — each run is independent)")
    void multipleRuns_allowedOnSameFile() throws Exception {
        Path pdf = tempDir.resolve("multi.pdf");
        Files.writeString(pdf, "bytes");
        ScoreFile row = scoreFileRepository.save(ScoreFile.forComposition(
                ownInA, "application/pdf", "multi.pdf", 5L, "sha", pdf.toString(), null));

        Long first = service.start(row.getId(), BAND_A);
        Long second = service.start(row.getId(), BAND_A);
        assertThat(first).isNotEqualTo(second);
        analysisRepository.findById(first).ifPresent(s -> assertThat(s.getRunnerRef()).startsWith("stub-"));
        analysisRepository.findById(second).ifPresent(s -> assertThat(s.getRunnerRef()).startsWith("stub-"));
    }

    @Test
    @DisplayName("poll() on an unknown (never-started) id throws IllegalStateException (→ 404 in the adapter)")
    void poll_unknownId_throwsIllegalState() {
        assertThatThrownBy(() -> service.poll(999_999L))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Layout sanity: two different analysis ids produce disjoint on-disk paths. */
    @Test
    void layout_pathsAreDeterministicAndDistinct() {
        Path first  = layout.outputFor(1L).resolve("arrangement.json");
        Path second = layout.outputFor(2L).resolve("arrangement.json");
        assertThat(first.toString()).isNotEqualTo(second.toString());
        assertThat(layout.outputFor(1L)).isEqualTo(layout.outputFor(1L));   // deterministic
    }
}
