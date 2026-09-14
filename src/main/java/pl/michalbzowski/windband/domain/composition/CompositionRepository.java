package pl.michalbzowski.windband.domain.composition;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for {@link Composition}. Implementations must
 * enforce band isolation — a query scoped to one band never returns another
 * band's compositions.
 */
public interface CompositionRepository {

    Composition save(Composition composition);

    Optional<Composition> findById(Long id);

    /** All (non-archived) compositions of the band, newest update first. */
    List<Composition> findAllByBand(Band band);

    boolean existsByIdAndBandId(Long id, Long bandId);

    void delete(Composition composition);
}
