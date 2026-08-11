package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface ItemAttributeDefRepository {

    ItemAttributeDef save(ItemAttributeDef def);

    Optional<ItemAttributeDef> findById(Long id);

    List<ItemAttributeDef> findAll();

    List<ItemAttributeDef> findByItemType(ItemType itemType);

    List<ItemAttributeDef> findByItemTypeAndBand(ItemType itemType, Band band);

    List<ItemAttributeDef> findGlobalByItemType(ItemType itemType);

    List<ItemAttributeDef> findActiveByItemTypeAndBand(ItemType itemType, Band band);

    List<ItemAttributeDef> findFilterableByItemTypeAndBand(ItemType itemType, Band band);

    List<ItemAttributeDef> findByDependsOnDefId(Long dependsOnDefId);

    void delete(ItemAttributeDef def);

    boolean existsByNameAndItemTypeAndBand(String name, ItemType itemType, Band band);
}