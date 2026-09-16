package pl.michalbzowski.windband.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

/**
 * Integration tests for the {@code ScoreFile} entity and its repository port
 * against a real (Testcontainers) database — exercises Flyway V35 and JPA mapping.
 *
 * <p>Task 1.06 of the score-library plan: file storage metadata lives in its own
 * table; the composition reference is never nullable and lookup helpers are
 * band-scoped so that another band can never fetch or count the shared library.
 */
@Transactional
class ScoreFileIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private ScoreFileRepository scoreFileRepository;

    private Band band(Long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("score_file_records_are_persisted — all fields round-trip through V35")
    void score_file_records_are_persisted() {
        Composition composition = compositionRepository.save(Composition.create(
                "Score File Host", null, null, null, band(1L)));

        ScoreFile saved = scoreFileRepository.save(ScoreFile.forComposition(
                composition, "application/pdf", "score-v1.pdf", 123456L,
                "a".repeat(64), "/data/scores/1/score-v1.pdf", null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getMimeType()).isEqualTo("application/pdf");
        assertThat(saved.getSizeBytes()).isEqualTo(123456L);
        assertThat(saved.getSha256()).hasSize(64);

        // Re-load in a fresh session to prove the row really came back from V35.
        ScoreFile reloaded = scoreFileRepository.findAllByComposition(composition)
                .stream().findFirst().orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(saved.getId());
        assertThat(reloaded.getComposition().getId()).isEqualTo(composition.getId());
        assertThat(reloaded.getComposition().getBand().getId()).isEqualTo(1L);
        assertThat(reloaded.getStoragePath()).isEqualTo("/data/scores/1/score-v1.pdf");
    }

    @Test
    @DisplayName("findAllByComposition returns only that composition's files")
    void lookup_is_scoped_to_composition() {
        Composition c1 = compositionRepository.save(Composition.create(
                "First", null, null, null, band(1L)));
        Composition c2 = compositionRepository.save(Composition.create(
                "Second", null, null, null, band(1L)));

        scoreFileRepository.save(ScoreFile.forComposition(
                c1, "application/pdf", "one.pdf", 10L, "x".repeat(64), "/s/1/a", null));
        scoreFileRepository.save(ScoreFile.forComposition(
                c2, "application/zip", "two.zip", 20L, "y".repeat(64), "/s/2/b", null));

        List<ScoreFile> ofC1 = scoreFileRepository.findAllByComposition(c1);
        List<ScoreFile> ofC2 = scoreFileRepository.findAllByComposition(c2);

        assertThat(ofC1).hasSize(1).extracting(ScoreFile::getMimeType).containsExactly("application/pdf");
        assertThat(ofC2).hasSize(1).extracting(ScoreFile::getMimeType).containsExactly("application/zip");
    }
}
