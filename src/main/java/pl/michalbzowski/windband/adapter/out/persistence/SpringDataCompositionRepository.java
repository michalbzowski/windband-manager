package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;

import java.util.List;
import java.util.Optional;

public interface SpringDataCompositionRepository extends JpaRepository<Composition, Long> {

    List<Composition> findAllByBandOrderByUpdatedAtDesc(Band band);

    /** US-1.02: band-scoped, case-insensitive match on title, composer or arranger. */
    @Query("SELECT c FROM Composition c WHERE c.band.id = :bandId AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           " LOWER(c.composer) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           " LOWER(c.arranger) LIKE LOWER(CONCAT('%', :term, '%')))")
    List<Composition> findByBandIdAndTitleOrComposerOrArrangerContainsIgnoreCase(
            @Param("bandId") Long bandId, @Param("term") String term);

    Optional<Composition> findByIdAndBandId(Long id, Long bandId);

    boolean existsByIdAndBandId(Long id, Long bandId);
}
