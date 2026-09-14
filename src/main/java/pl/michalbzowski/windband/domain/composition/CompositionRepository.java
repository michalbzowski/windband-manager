package pl.michalbzowski.windband.domain.composition;

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

    boolean existsByIdAndBandId(Long id, Long bandId);

    void delete(Composition composition);
}
