package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface OrderAttributeValueRepository {
    OrderAttributeValue save(OrderAttributeValue value);
    Optional<OrderAttributeValue> findByOrderAndAttributeDef(InventoryOrder order, OrderAttributeDef def);
    List<OrderAttributeValue> findByOrder(InventoryOrder order);
    void delete(OrderAttributeValue value);
}
