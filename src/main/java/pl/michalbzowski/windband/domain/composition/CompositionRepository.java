package pl.michalbzowski.windband.domain.composition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for {@link Composition}. Implementations must
 * enforce band isolation — every read is scoped to a band, so no query
 * can ever return another band's compositions.
 */
public interface CompositionRepository {

    Composition save(Composition composition);

    /** Only resolves the composition if it belongs to the given band; {@link Optional#empty()} otherwise. */
    Optional<Composition> findByIdAndBandId(Long id, Long bandId);

    /** All compositions of the band, newest update first (archiving handled by caller). */
    List<Composition> findAllByBand(Band band);

    /** Paginated version of {@link #findAllByBand(Band)}. */
    Page<Composition> findAllByBand(Band band, Pageable pageable);

    /** Paginated version filtered by status. */
    Page<Composition> findAllByBandAndStatus(Band band, CompositionStatus status, Pageable pageable);

    /** All rows of the band matching one status (newest update first). */
    List<Composition> listAllByBandAndStatus(Band band, CompositionStatus status);

    /** US-1.02: case-insensitive search across title, composer and arranger within one band. */
    List<Composition> search(Long bandId, String term);

    boolean existsByIdAndBandId(Long id, Long bandId);

    void delete(Composition composition);
}
