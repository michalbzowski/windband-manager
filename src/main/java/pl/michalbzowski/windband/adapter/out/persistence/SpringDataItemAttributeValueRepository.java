package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValue;

import java.util.List;
import java.util.Optional;

public interface SpringDataItemAttributeValueRepository extends JpaRepository<ItemAttributeValue, Long> {

    List<ItemAttributeValue> findByItemId(Long itemId);

    List<ItemAttributeValue> findByAttributeDefId(Long attributeDefId);

    Optional<ItemAttributeValue> findByItemIdAndAttributeDef(Long itemId, ItemAttributeDef attributeDef);

    void deleteByItemId(Long itemId);

    void deleteByAttributeDefId(Long attributeDefId);
}
