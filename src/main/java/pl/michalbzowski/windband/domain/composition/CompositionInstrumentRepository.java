package pl.michalbzowski.windband.domain.composition;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for {@link CompositionInstrument} (US-1.3).
 *
 * <p><b>Band isolation:</b> this port returns rows only after the caller has proved the
 * composition belongs to the calling band. The underlying query path is composition-
 * scoped — a row cannot float free without its parent, so "band-scoped" reduces to
 * "composition-scoped" here. Callers (services) are responsible for the band check up
 * front; this port keeps that guarantee by not exposing any unscoped row-fetch.
 *
 * <p><b>Cross-band writes are rejected before persistence</b> by the entity factory
 * ({@code CompositionInstrument.forComposition}) which requires the (composition,
 * instrument) pair to share a band id. The DB constraint is a defense-in-depth net
 * for anything that ever bypasses the Java path (manual SQL, admin tooling).
 */
public interface CompositionInstrumentRepository {

    CompositionInstrument save(CompositionInstrument partition);

    Optional<CompositionInstrument> findById(Long id);

    /** All parts of the given composition (the dominant read target — Parts tab of US-3.03). */
    List<CompositionInstrument> findAllByComposition(Composition composition);

    /** Paginated version — for large libraries where a single query can span pages. */
    Page<CompositionInstrument> findAllByComposition(Composition composition, Pageable pageable);

    /** The exact (composition, instrument role) pair — useful for unique lookups and upserts. */
    Optional<CompositionInstrument> findByCompositionAndInstrumentRole(Composition composition, String role);

    /** A convenient one-shot lookup by (compositionId, role). */
    Optional<CompositionInstrument> findByCompositionIdAndInstrumentRole(Long compositionId, String role);

    /** All parts of the given band across its compositions — for US-5.x distribution previews. */
    List<CompositionInstrument> findAllByBand(Band band);

    boolean existsByCompositionId(Long compositionId);

    void delete(CompositionInstrument partition);

    /** Bulk remove all parts attached to a composition (the cascade path in tests + manual deletes). */
    int deleteAllByCompositionId(Long compositionId);
}
