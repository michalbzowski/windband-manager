package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDefRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataOrderAttributeDefRepository extends JpaRepository<OrderAttributeDef, Long>, OrderAttributeDefRepository {
    List<OrderAttributeDef> findByBandOrderByName(Band band);

    @Override
    Optional<OrderAttributeDef> findByBandAndName(Band band, String name);
}
