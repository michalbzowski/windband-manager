package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain {@link CompositionInstrumentRepository} port on top of
 * {@link SpringDataCompositionInstrumentRepository}.
 *
 * <p>Inherits the same lazy-init discipline (JOIN FETCH) as every other adapter in this module:
 * consumers routinely traverse the {@code ci.composition.band} chain, so all association-returning
 * queries pre-warm those associations rather than leaking the JPA proxy.
 */
@Component
public class CompositionInstrumentRepositoryAdapter implements CompositionInstrumentRepository {

    private final SpringDataCompositionInstrumentRepository springData;

    public CompositionInstrumentRepositoryAdapter(SpringDataCompositionInstrumentRepository springData) {
        this.springData = springData;
    }

    @Override
    public CompositionInstrument save(CompositionInstrument partition) { return springData.save(partition); }

    @Override
    public Optional<CompositionInstrument> findById(Long id) { return springData.findById(id); }

    @Override
    public List<CompositionInstrument> findAllByComposition(Composition composition) {
        return springData.findAllByComposition(composition);
    }

    @Override
    public Page<CompositionInstrument> findAllByComposition(Composition composition, Pageable pageable) {
        return springData.findAllByComposition(composition, pageable);
    }

    @Override
    public Optional<CompositionInstrument> findByCompositionAndInstrumentRole(Composition composition, String role) {
        return springData.findByCompositionAndInstrumentRole(composition, role);
    }

    @Override
    public Optional<CompositionInstrument> findByCompositionIdAndInstrumentRole(Long compositionId, String role) {
        return springData.findByCompositionIdAndInstrumentRole(compositionId, role);
    }

    @Override
    public List<CompositionInstrument> findAllByBand(Band band) { return springData.findAllByBand(band); }

    @Override
    public boolean existsByCompositionId(Long compositionId) { return springData.existsByCompositionId(compositionId); }

    @Override
    public void delete(CompositionInstrument partition) { springData.delete(partition); }

    @Override
    public int deleteAllByCompositionId(Long compositionId) { return springData.deleteAllByCompositionId(compositionId); }
}
