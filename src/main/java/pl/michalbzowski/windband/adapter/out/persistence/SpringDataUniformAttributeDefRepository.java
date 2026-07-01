package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for UniformAttributeDef.
 *
 * Extends ONLY JpaRepository (NOT the domain interface) to avoid the
 * CrudRepository method-name collision. See SpringDataInstrumentAttributeDefRepository
 * for the full explanation.
 */
public interface SpringDataUniformAttributeDefRepository extends JpaRepository<UniformAttributeDef, Long> {
    List<UniformAttributeDef> findByBandAndActiveTrueOrderByDisplayOrder(Band band);
    List<UniformAttributeDef> findByBandOrderByDisplayOrder(Band band);
    Optional<UniformAttributeDef> findByBandAndName(Band band, String name);
}
