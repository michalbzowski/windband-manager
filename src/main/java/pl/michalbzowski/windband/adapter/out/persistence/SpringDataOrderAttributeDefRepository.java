package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for OrderAttributeDef.
 *
 * Extends ONLY JpaRepository (NOT the domain interface) to avoid the
 * CrudRepository method-name collision. See SpringDataInstrumentAttributeDefRepository
 * for the full explanation.
 */
public interface SpringDataOrderAttributeDefRepository extends JpaRepository<OrderAttributeDef, Long> {
    List<OrderAttributeDef> findByBandOrderByDisplayOrder(Band band);
    Optional<OrderAttributeDef> findByBandAndName(Band band, String name);
}
