package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface OrderAttributeDefRepository {
    OrderAttributeDef save(OrderAttributeDef def);
    List<OrderAttributeDef> findByBand(Band band);
    Optional<OrderAttributeDef> findById(Long id);
    void delete(OrderAttributeDef def);
}
