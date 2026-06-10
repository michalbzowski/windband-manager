package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface OrderAttributeDefRepository {
    List<OrderAttributeDef> findByBand(Band band);
    Optional<OrderAttributeDef> findByBandAndName(Band band, String name);
    OrderAttributeDef save(OrderAttributeDef def);
    Optional<OrderAttributeDef> findById(Long id);
    void delete(OrderAttributeDef def);
}
