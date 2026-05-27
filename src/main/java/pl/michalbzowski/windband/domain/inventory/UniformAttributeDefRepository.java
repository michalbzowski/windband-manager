package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface UniformAttributeDefRepository {
    UniformAttributeDef save(UniformAttributeDef def);
    List<UniformAttributeDef> findByBand(Band band);
    Optional<UniformAttributeDef> findById(Long id);
    void delete(UniformAttributeDef def);
}
