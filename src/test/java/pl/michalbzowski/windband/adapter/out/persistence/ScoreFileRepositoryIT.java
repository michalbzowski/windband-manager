package pl.michalbzowski.windband.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.util.List;

/**
 * Integration test for the ScoreFile → Composition association lookup.
 *
 * <p><b>Why this test exists:</b> when {@code findAllByComposition} was originally written with a JPQL query
 * like {@code WHERE sf.composition = :composition}, the entity identity comparison (Hibernate default,
 * since {@link Composition} has no custom {@code equals()}) meant that passing a <i>different</i> instance of
 * the same composition (e.g. one loaded in a separate transaction/scope, as happens when
 * {@code ScoreFileListQueryService.listByComposition} first fetches the composition and then queries files)
 * returned an <b>empty</b> list instead of the stored rows. This test reproduces that production scenario
 * explicitly and verifies the repository contract by identifier (persistent id), not object equality.
 */
@Transactional
class ScoreFileRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private pl.michalbzowski.windband.domain.composition.CompositionRepository compositionRepository;

    @Autowired
    private ScoreFileRepository scoreFileRepository;

    @Test
    @DisplayName("findAllByComposition resolves by identifier, not object identity (production bug)")
    void findAllByComposition_resolvesByIdNotIdentity() {
        // Arrange: create a band + composition + one score file via the repository port.
        var band = bandRepository.findById(1L).orElseThrow();
        Composition original = compositionRepository.save(
                Composition.create("Identity-Lookup Test", null, null, null, band));
        assertThat(original.getId()).isNotNull();

        scoreFileRepository.save(ScoreFile.forComposition(
                original, "application/pdf", "score-identity-test.pdf", 42L,
                "d".repeat(64), "/data/scores/1/d-test.pdf", null));

        // Baseline: files are visible when queried with the same (persisted) instance.
        List<ScoreFile> viaOriginal = scoreFileRepository.findAllByComposition(original);
        assertThat(viaOriginal).hasSize(1)
                .first().satisfies(sf -> {
                    assertThat(sf.getOriginalName()).isEqualTo("score-identity-test.pdf");
                    assertThat(sf.getComposition().getId()).isEqualTo(original.getId());
                });

        // Act: load a fresh Composition instance for the same row (separate query path, typical cross-service).
        Composition reloaded = compositionRepository.findByIdAndBandId(original.getId(), band.getId())
                .orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(original.getId());
        assertThat((long) original.getId()).isNotEqualTo(0L); // sanity: id is present

        // This is the production call shape: ScoreFileListQueryService.listByComposition(id, bandId).
        List<ScoreFile> viaReloaded = scoreFileRepository.findAllByComposition(reloaded);

        // Assert: the contract is "files of this composition" regardless of which Java instance we passed.
        assertThat(viaReloaded).hasSize(1)
                .first().satisfies(sf -> {
                    assertThat(sf.getOriginalName()).isEqualTo("score-identity-test.pdf");
                    assertThat(sf.getComposition().getId()).isEqualTo(reloaded.getId());
                });

        // And findLatestByComposition behaves the same way.
        var latest = scoreFileRepository.findLatestByComposition(reloaded).orElseThrow();
        assertThat(latest.getOriginalName()).isEqualTo("score-identity-test.pdf");
    }
}
