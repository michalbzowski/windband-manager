package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;

import java.util.List;

public interface SpringDataOrderAttributeDefRepository extends JpaRepository<OrderAttributeDef, Long> {
    List<OrderAttributeDef> findByBandOrderByName(Band band);
}
