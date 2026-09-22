package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.composition.ScoreFile;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data adapter for the {@link ScoreFile} row.
 *
 * <p><b>Lazy-assoc warning:</b> consumers routinely call
 * {@code scoreFile.getComposition().getBand()} (e.g. band-ACL checks around the
 * future download endpoint), so every association-returning query below uses an
 * explicit {@code JOIN FETCH sf.composition} rather than a bare ID filter —
 * the same rule and incident documented on
 * {@link SpringDataCompositionRepository}.
 */
public interface SpringDataScoreFileRepository extends JpaRepository<ScoreFile, Long> {

    @Query("SELECT sf FROM ScoreFile sf JOIN FETCH sf.composition WHERE sf.composition.id = :compositionId ORDER BY sf.id DESC")
    List<ScoreFile> listAllByCompositionId(@Param("compositionId") Long compositionId);

    Optional<ScoreFile> findFirstByCompositionIdOrderByIdDesc(Long compositionId);

    boolean existsByCompositionId(Long compositionId);

    List<ScoreFile> findByParentFileId(Long parentFileId);
}
