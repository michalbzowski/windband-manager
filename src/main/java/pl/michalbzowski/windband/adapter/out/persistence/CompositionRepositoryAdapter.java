package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain {@link CompositionRepository} port on top of
 * {@link SpringDataCompositionRepository}. Kept thin: Spring Data already enforces
 * band-scoped queries (the band is part of the query), so the adapter only wires.
 */
@Component
public class CompositionRepositoryAdapter implements CompositionRepository {

    private final SpringDataCompositionRepository springData;

    public CompositionRepositoryAdapter(SpringDataCompositionRepository springData) {
        this.springData = springData;
    }

    @Override
    public Composition save(Composition composition) {
        return springData.save(composition);
    }

    @Override
    public Optional<Composition> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public List<Composition> findAllByBand(Band band) {
        // Spring Data method name scoping guarantees only that band's compositions are returned.
        return springData.findAllByBandOrderByUpdatedAtDesc(band);
    }

    @Override
    public boolean existsByIdAndBandId(Long id, Long bandId) {
        return springData.existsByIdAndBandId(id, bandId);
    }

    @Override
    public void delete(Composition composition) {
        springData.delete(composition);
    }
}
