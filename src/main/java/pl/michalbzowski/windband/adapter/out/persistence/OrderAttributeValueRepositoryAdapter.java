package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.InventoryOrder;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeDef;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeValue;
import pl.michalbzowski.windband.domain.inventory.OrderAttributeValueRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderAttributeValueRepositoryAdapter implements OrderAttributeValueRepository {

    private final SpringDataOrderAttributeValueRepository springDataRepo;

    @Override
    public OrderAttributeValue save(OrderAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<OrderAttributeValue> findByOrderAndAttributeDef(InventoryOrder order, OrderAttributeDef def) {
        return springDataRepo.findByOrderAndAttributeDef(order, def);
    }

    @Override
    public List<OrderAttributeValue> findByOrder(InventoryOrder order) {
        return springDataRepo.findByOrder(order);
    }

    @Override
    public void delete(OrderAttributeValue value) {
        springDataRepo.delete(value);
    }
}
