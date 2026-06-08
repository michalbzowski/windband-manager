package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDefRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataUniformAttributeDefRepository extends JpaRepository<UniformAttributeDef, Long>, UniformAttributeDefRepository {
    List<UniformAttributeDef> findByBandOrderByName(Band band);

    @Override
    Optional<UniformAttributeDef> findByBandAndName(Band band, String name);
}
