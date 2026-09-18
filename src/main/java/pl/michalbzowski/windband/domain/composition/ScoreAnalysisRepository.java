package pl.michalbzowski.windband.domain.composition;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ScoreAnalysis} (US-4.1). Inherits full CRUD
 * from {@link JpaRepository}. The two custom method names we use at runtime are:
 * <ul>
 *   <li>{@link #findFirstByCompositionIdOrderByIdDesc(Long)} — the US-4.1 "latest run" read;</li>
 *   <li>{@link #findById(Long)} — inherited; used after start() has already band-isolated.</li>
 * </ul>
 * The band isolation is performed at {@code start()} time through the composition's
 * band check; the row itself stores only a composition_id FK (no band column) and we
 * never read it naked from the DB without going through a composition first.
 */
@Repository
public interface ScoreAnalysisRepository extends JpaRepository<ScoreAnalysis, Long> {

    /** Most recent run for a given composition (null when none has been started yet). */
    Optional<ScoreAnalysis> findFirstByCompositionIdOrderByIdDesc(Long compositionId);

    boolean existsByCompositionId(Long compositionId);

    /**
     * US-4.1 "latest" endpoint helper: band-scoped by construction — the caller MUST
     * pass a {@code compositionId} whose own band has already been validated through
     * the calling band's path. Empty Optional → 404 in the adapter.
     */
    @Query("SELECT a FROM ScoreAnalysis a WHERE a.composition.id = :compId " +
            "AND a.composition.band.id = :bandId ORDER BY a.id DESC LIMIT 1")
    Optional<ScoreAnalysis> findLatestByCompositionIdAndBandId(
            @Param("compId") Long compositionId,
            @Param("bandId") Long bandId);
}
