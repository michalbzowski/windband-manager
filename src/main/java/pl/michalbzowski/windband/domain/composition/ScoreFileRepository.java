package pl.michalbzowski.windband.domain.composition;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for {@link ScoreFile} — uploaded score files of a
 * composition. Every read is scoped to the composition, mirroring the band-scoped
 * contract of {@link CompositionRepository} (a file never floats free).
 *
 * <p>Used by the future file download endpoint (Task 1.08) and cascade-deletion
 * lifecycle (Task 1.09); tests in Task 1.06 already pin down the contract.
 */
public interface ScoreFileRepository {

    ScoreFile save(ScoreFile scoreFile);

    Optional<ScoreFile> findById(Long id);

    /** All files of the composition, newest first. */
    List<ScoreFile> findAllByComposition(Composition composition);

    /** The most recently uploaded file of a composition — primary read target. */
    Optional<ScoreFile> findLatestByComposition(Composition composition);

    boolean existsByCompositionId(Long compositionId);

    void delete(ScoreFile scoreFile);
}
