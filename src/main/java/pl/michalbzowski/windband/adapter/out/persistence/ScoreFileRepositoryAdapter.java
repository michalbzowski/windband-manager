package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter implementing the domain {@link ScoreFileRepository} port on top of
 * {@link SpringDataScoreFileRepository}.
 *
 * <p>Inherits the same Shape-D (lazy-init) discipline as the compositions module:
 * every association-returning query carries a {@code JOIN FETCH sf.composition},
 * so consumers may safely traverse the composition association after this port
 * returns.
 */
@Component
public class ScoreFileRepositoryAdapter implements ScoreFileRepository {

    private final SpringDataScoreFileRepository springData;

    public ScoreFileRepositoryAdapter(SpringDataScoreFileRepository springData) {
        this.springData = springData;
    }

    @Override
    public ScoreFile save(ScoreFile scoreFile) {
        return springData.save(scoreFile);
    }

    @Override
    public Optional<ScoreFile> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public List<ScoreFile> findAllByComposition(Composition composition) {
        return springData.findAllByComposition(composition);
    }

    @Override
    public Optional<ScoreFile> findLatestByComposition(Composition composition) {
        return springData.findLatestByComposition(composition);
    }

    @Override
    public boolean existsByCompositionId(Long compositionId) {
        return springData.existsByCompositionId(compositionId);
    }

    @Override
    public void delete(ScoreFile scoreFile) {
        springData.delete(scoreFile);
    }
}
