package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain {@link CompositionRepository} port on top of
 * {@link SpringDataCompositionRepository}. Every query is band-scoped by design, so
 * the adapter only wires calls through.
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
    public Optional<Composition> findByIdAndBandId(Long id, Long bandId) {
        return springData.findByIdAndBandId(id, bandId);
    }

    @Override
    public List<Composition> findAllByBand(Band band) {
        return springData.findAllByBandOrderByUpdatedAtDesc(band);
    }

    @Override
    public Page<Composition> findAllByBand(Band band, Pageable pageable) {
        return springData.findAllByBand(band, pageable);
    }

    @Override
    public Page<Composition> findAllByBandAndStatus(Band band, CompositionStatus status, Pageable pageable) {
        return springData.findAllByBandAndStatus(band, status, pageable);
    }

    @Override
    public List<Composition> listAllByBandAndStatus(Band band, CompositionStatus status) {
        return springData.listAllByBandAndStatus(band, status);
    }

    @Override
    public List<Composition> search(Long bandId, String term) {
        if (term == null || term.isBlank()) {
            return List.of();
        }
        return springData.findByBandIdAndTitleOrComposerOrArrangerContainsIgnoreCase(bandId, term.trim());
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
