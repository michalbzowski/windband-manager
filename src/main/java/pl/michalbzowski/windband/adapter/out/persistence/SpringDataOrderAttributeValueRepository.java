package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeValue;

import java.util.List;
import java.util.Optional;

public interface SpringDataOrderAttributeValueRepository extends JpaRepository<OrderAttributeValue, Long> {
    Optional<OrderAttributeValue> findByOrderAndAttributeDef(InventoryOrder order, OrderAttributeDef def);
    List<OrderAttributeValue> findByOrder(InventoryOrder order);
}
