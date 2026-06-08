package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.adapter.out.persistence.OrderAttributeDefRepositoryAdapter;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderAttributeCommandService {

    private final OrderAttributeDefRepositoryAdapter defRepository;
    private final OrderAttributeValueRepository valueRepository;
    private final InventoryRepository inventoryRepository;

    public OrderAttributeDef createAttributeDef(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        OrderAttributeDef def = OrderAttributeDef.create(band, name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public OrderAttributeDef updateAttributeDef(Long id, String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
        OrderAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OrderAttributeDef not found: " + id));
        def.update(name, type, required, displayInList, displayOrder, options);
        def.setDependsOnAttributeId(dependsOnAttributeId);
        def.setDependsOnValue(dependsOnValue);
        return defRepository.save(def);
    }

    public void deleteAttributeDef(Long id) {
        OrderAttributeDef def = defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OrderAttributeDef not found: " + id));
        defRepository.delete(def);
    }

    public OrderAttributeDef getAttributeDefById(Long id) {
        return defRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OrderAttributeDef not found: " + id));
    }

    public void setAttributeValue(Long orderId, Long attributeDefId, String value) {
        InventoryOrder order = inventoryRepository.findOrderById(orderId)
                .orElseThrow(() -> new InventoryOrderNotFoundException(orderId));
        OrderAttributeDef def = defRepository.findById(attributeDefId)
                .orElseThrow(() -> new IllegalArgumentException("OrderAttributeDef not found: " + attributeDefId));
        setAttributeValue(order, def, value);
    }

    public void setAttributeValue(InventoryOrder order, OrderAttributeDef def, String value) {
        OrderAttributeValue attrValue = valueRepository.findByOrderAndAttributeDef(order, def)
                .orElse(null);
        if (attrValue == null) {
            attrValue = OrderAttributeValue.create(order, def, value);
        } else {
            attrValue.setValue(value);
        }
        valueRepository.save(attrValue);
    }
}
