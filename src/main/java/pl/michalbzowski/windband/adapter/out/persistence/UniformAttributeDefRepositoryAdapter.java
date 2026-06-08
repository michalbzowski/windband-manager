package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDefRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UniformAttributeDefRepositoryAdapter implements UniformAttributeDefRepository {

    private final SpringDataUniformAttributeDefRepository springDataRepo;

    @Override
    public List<UniformAttributeDef> findByBand(Band band) {
        return springDataRepo.findByBandOrderByName(band);
    }

    @Override
    public Optional<UniformAttributeDef> findByBandAndName(Band band, String name) {
        return springDataRepo.findByBandAndName(band, name);
    }

    // Additional methods delegated directly to Spring Data (not in domain interface to avoid CrudRepository conflict)
    public UniformAttributeDef save(UniformAttributeDef def) {
        return springDataRepo.save(def);
    }

    public Optional<UniformAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    public void delete(UniformAttributeDef def) {
        springDataRepo.delete(def);
    }
}
