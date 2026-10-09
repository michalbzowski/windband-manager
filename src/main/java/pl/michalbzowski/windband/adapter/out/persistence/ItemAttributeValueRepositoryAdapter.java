package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValue;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValueRepository;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ItemAttributeValueRepositoryAdapter implements ItemAttributeValueRepository {

    private final SpringDataItemAttributeValueRepository springDataRepo;

    @Override
    public ItemAttributeValue save(ItemAttributeValue value) {
        return springDataRepo.save(value);
    }

    @Override
    public Optional<ItemAttributeValue> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<ItemAttributeValue> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<ItemAttributeValue> findByItemId(Long itemId) {
        return springDataRepo.findByItemId(itemId);
    }

    @Override
    public List<ItemAttributeValue> findByAttributeDefId(Long attributeDefId) {
        return springDataRepo.findByAttributeDefId(attributeDefId);
    }

    @Override
    public List<ItemAttributeValue> findByItemIdAndAttributeDefId(Long itemId, Long attributeDefId) {
        return springDataRepo.findByItemId(itemId).stream()
                .filter(v -> v.getAttributeDef().getId().equals(attributeDefId))
                .toList();
    }

    @Override
    public Optional<ItemAttributeValue> findByItemIdAndAttributeDef(Long itemId, ItemAttributeDef attributeDef) {
        return springDataRepo.findByItemIdAndAttributeDef(itemId, attributeDef);
    }

    @Override
    public void delete(ItemAttributeValue value) {
        springDataRepo.delete(value);
    }

    @Override
    public void deleteByItemId(Long itemId) {
        springDataRepo.deleteByItemId(itemId);
    }

    @Override
    public void deleteByAttributeDefId(Long attributeDefId) {
        springDataRepo.deleteByAttributeDefId(attributeDefId);
    }
}
