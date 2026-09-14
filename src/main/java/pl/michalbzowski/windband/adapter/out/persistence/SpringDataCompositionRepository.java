package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataCompositionRepository extends JpaRepository<Composition, Long> {

    List<Composition> findAllByBandOrderByUpdatedAtDesc(Band band);

    Optional<Composition> findTopByIdOrderByIdDesc(Long id);

    @Query("SELECT c FROM Composition c WHERE c.band.id = :bandId")
    List<Composition> findByBandId(@Param("bandId") Long bandId);

    @Query("SELECT COUNT(c) > 0 FROM Composition c WHERE c.id = :id AND c.band.id = :bandId")
    boolean existsByIdAndBandId(@Param("id") Long id, @Param("bandId") Long bandId);

    @Query("SELECT c FROM Composition c WHERE c.band.id = :bandId AND c.status <> :excluded " +
           "AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "     LOWER(c.composer) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "     LOWER(c.arranger) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY c.updatedAt DESC")
    List<Composition> search(@Param("bandId") Long bandId, @Param("excluded") CompositionStatus excluded,
                             @Param("term") String term);
}
