package pl.michalbzowski.windband.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDefRepository;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ItemAttributeDefRepositoryAdapter implements ItemAttributeDefRepository {

    private final SpringDataItemAttributeDefRepository springDataRepo;

    @Override
    public ItemAttributeDef save(ItemAttributeDef def) {
        return springDataRepo.save(def);
    }

    @Override
    public Optional<ItemAttributeDef> findById(Long id) {
        return springDataRepo.findById(id);
    }

    @Override
    public List<ItemAttributeDef> findAll() {
        return springDataRepo.findAll();
    }

    @Override
    public List<ItemAttributeDef> findByItemType(ItemType itemType) {
        return springDataRepo.findActiveByItemTypeOrderByIdAsc(itemType);
    }

    @Override
    public List<ItemAttributeDef> findByItemTypeAndBand(ItemType itemType, Band band) {
        List<ItemAttributeDef> merged = new ArrayList<>();
        merged.addAll(springDataRepo.findByBandIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc());
        springDataRepo.findByBandIdAndActiveTrueOrderByDisplayOrderAscIdAsc(band.getId())
                .stream().filter(d -> Objects.equals(d.getItemType(), itemType))
                .forEach(merged::add);
        return merged;
    }

    @Override
    public List<ItemAttributeDef> findGlobalByItemType(ItemType itemType) {
        return springDataRepo.findByBandIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .filter(d -> Objects.equals(d.getItemType(), itemType))
                .toList();
    }

    @Override
    public List<ItemAttributeDef> findActiveByItemTypeAndBand(ItemType itemType, Band band) {
        return findByItemTypeAndBand(itemType, band).stream()
                .filter(ItemAttributeDef::isActive)
                .sorted(Comparator.comparingInt(ItemAttributeDef::getDisplayOrder).thenComparingLong(ItemAttributeDef::getId))
                .toList();
    }

    @Override
    public List<ItemAttributeDef> findFilterableByItemTypeAndBand(ItemType itemType, Band band) {
        return findActiveByItemTypeAndBand(itemType, band).stream()
                .filter(ItemAttributeDef::isFilterable)
                .toList();
    }

    @Override
    public List<ItemAttributeDef> findByDependsOnDefId(Long dependsOnDefId) {
        return springDataRepo.findAll().stream()
                .filter(d -> Objects.equals(d.getDependsOnDefId(), dependsOnDefId))
                .toList();
    }

    @Override
    public void delete(ItemAttributeDef def) {
        springDataRepo.delete(def);
    }

    @Override
    public boolean existsByNameAndItemTypeAndBand(String name, ItemType itemType, Band band) {
        return findByItemTypeAndBand(itemType, band).stream()
                .anyMatch(d -> d.getName().equals(name));
    }
}
