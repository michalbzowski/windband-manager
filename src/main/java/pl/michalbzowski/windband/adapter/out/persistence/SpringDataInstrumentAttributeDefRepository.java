package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDef;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for InstrumentAttributeDef.
 *
 * Extends ONLY JpaRepository (NOT the domain interface) to avoid the
 * CrudRepository method-name collision that happens when both are extended
 * and the domain interface declares save/findById/delete.
 *
 * Custom query methods are declared here per Spring Data JPA convention.
 * The domain interface (InstrumentAttributeDefRepository) has the matching
 * signatures, but is NOT extended by this SpringData interface.
 */
public interface SpringDataInstrumentAttributeDefRepository extends JpaRepository<InstrumentAttributeDef, Long> {
    List<InstrumentAttributeDef> findByBandOrderByName(Band band);
    Optional<InstrumentAttributeDef> findByBandAndName(Band band, String name);
}
