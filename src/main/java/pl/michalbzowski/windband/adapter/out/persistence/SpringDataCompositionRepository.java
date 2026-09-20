package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data adapter for the {@link Composition} aggregate.
 *
 * <p><b>Lazy-assoc warning:</b> JPQL {@code c.band.id = :bandId} does NOT initialize the
 * lazy {@code band} association (Hibernate treats it as a scalar filter), so any
 * consumer calling {@code composition.getBand()} after the repository returns —
 * a controller, a query service, a test asserting on the FK — throws
 * {@code LazyInitializationException}. To defend against Shape-B of that bug class
 * (see {@code spring-boot-selenium-tests} skill), this repository uses explicit
 * {@code JOIN FETCH band} instead of bare ID-based filters.
 */
public interface SpringDataCompositionRepository extends JpaRepository<Composition, Long> {

    List<Composition> findAllByBandOrderByUpdatedAtDesc(Band band);

    /** Paginated version with JOIN FETCH to keep band association initialized. */
    @Query("SELECT c FROM Composition c JOIN FETCH c.band WHERE c.band = :band ORDER BY c.updatedAt DESC")
    Page<Composition> findAllByBand(@Param("band") Band band, Pageable pageable);

    /** All rows of the band with the given status — newest update first (used for in-memory text filtering). */
    @Query("SELECT c FROM Composition c JOIN FETCH c.band WHERE c.band = :band AND c.status = :status ORDER BY c.updatedAt DESC")
    List<Composition> listAllByBandAndStatus(@Param("band") Band band, @Param("status") CompositionStatus status);

    /** Paginated version filtered by status with JOIN FETCH. */
    @Query("SELECT c FROM Composition c JOIN FETCH c.band WHERE c.band = :band AND c.status = :status ORDER BY c.updatedAt DESC")
    Page<Composition> findAllByBandAndStatus(@Param("band") Band band, @Param("status") CompositionStatus status, Pageable pageable);

    /**
     * US-1.02: band-scoped, case-insensitive match on title, composer or arranger.
     * Returns rows most-recently-updated first (contract of the list view).
     */
    @Query("SELECT c FROM Composition c JOIN FETCH c.band WHERE c.band.id = :bandId AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           " LOWER(c.composer) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           " LOWER(c.arranger) LIKE LOWER(CONCAT('%', :term, '%'))) ORDER BY c.updatedAt DESC")
    List<Composition> findByBandIdAndTitleOrComposerOrArrangerContainsIgnoreCase(
            @Param("bandId") Long bandId, @Param("term") String term);

    /**
     * Cross-band safe lookup: returns the composition only if it belongs to that band.
     * Band-scoped by {@code c.band.id} + JOIN FETCH so {@code getBand()} stays safe.
     */
    @Query("SELECT c FROM Composition c JOIN FETCH c.band WHERE c.id = :id AND c.band.id = :bandId")
    Optional<Composition> findByIdAndBandId(@Param("id") Long id, @Param("bandId") Long bandId);

    boolean existsByIdAndBandId(Long id, Long bandId);
}

