package pl.michalbzowski.windband.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.composition.PartShareToken;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;

/**
 * JPA adapter for the {@link PartShareTokenRepository} domain port (US-7.11).
 * Thin delegation to {@link SpringDataPartShareTokenRepository}; all association
 * pre-warming lives in the Spring Data queries.
 */
@Component
public class PartShareTokenRepositoryAdapter implements PartShareTokenRepository {

    private final SpringDataPartShareTokenRepository springData;

    public PartShareTokenRepositoryAdapter(SpringDataPartShareTokenRepository springData) {
        this.springData = springData;
    }

    @Override public PartShareToken save(PartShareToken token) { return springData.save(token); }
    @Override public Optional<PartShareToken> findByToken(UUID token) { return springData.findByToken(token); }
    @Override public Optional<PartShareToken> findByPartId(Long partId) { return springData.findByPartId(partId); }
    @Override public List<PartShareToken> findAllByPartId(Long partId) { return springData.findAllByPartId(partId); }
    @Override public void delete(PartShareToken token) { springData.delete(token); }
}
