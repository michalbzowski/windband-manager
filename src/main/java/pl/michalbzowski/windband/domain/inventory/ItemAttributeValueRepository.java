package pl.michalbzowski.windband.domain.inventory;

import java.util.List;
import java.util.Optional;

public interface ItemAttributeValueRepository {

    ItemAttributeValue save(ItemAttributeValue value);

    Optional<ItemAttributeValue> findById(Long id);

    List<ItemAttributeValue> findAll();

    List<ItemAttributeValue> findByItemId(Long itemId);

    List<ItemAttributeValue> findByAttributeDefId(Long attributeDefId);

    List<ItemAttributeValue> findByItemIdAndAttributeDefId(Long itemId, Long attributeDefId);

    Optional<ItemAttributeValue> findByItemIdAndAttributeDef(Long itemId, ItemAttributeDef attributeDef);

    void delete(ItemAttributeValue value);

    void deleteByItemId(Long itemId);

    void deleteByAttributeDefId(Long attributeDefId);
}