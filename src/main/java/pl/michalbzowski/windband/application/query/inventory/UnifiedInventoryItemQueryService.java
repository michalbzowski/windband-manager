package pl.michalbzowski.windband.application.query.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.InventoryItemRepository;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDefRepository;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValueRepository;
import pl.michalbzowski.windband.domain.inventory.ItemType;
import pl.michalbzowski.windband.application.command.inventory.InventoryItemNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified read service for the single concrete {@link InventoryItem} model (F2 step 1).
 * Replaces InventoryQueryService / AwardQueryService / InventoryAttributeQueryService.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UnifiedInventoryItemQueryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemAttributeDefRepository itemAttributeDefRepository;
    private final ItemAttributeValueRepository itemAttributeValueRepository;
    private final BandRepository bandRepository;

    public List<InventoryItem> getAllItemsByBand(Long bandId) {
        if (bandId == null) return List.of();
        return inventoryItemRepository.findWithMembersByBandId(bandId);
    }

    public InventoryItem getItemById(Long id) {
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    public List<InventoryItem> getItemsByBandAndType(Long bandId, ItemType type) {
        if (bandId == null || type == null) return List.of();
        return inventoryItemRepository.findAllByBandIdAndType(bandId, type);
    }

    /**
     * Active attribute definitions for a type: global + band-scoped, ordered by displayOrder then id.
     */
    public List<ItemAttributeDef> getAttributeDefsForType(Long bandId, ItemType type) {
        if (type == null) return List.of();
        Band band = bandId != null ? bandRepository.findById(bandId).orElse(null) : null;
        if (band == null) {
            return itemAttributeDefRepository.findGlobalByItemType(type);
        }
        return itemAttributeDefRepository.findActiveByItemTypeAndBand(type, band);
    }

    /** Total active items of a band, any type (dashboard counter). */
    public long getBandItemCount(Long bandId) {
        return getAllItemsByBand(bandId).size();
    }

    /** Item attribute values as defId -> valueText. */
    public Map<Long, String> getAttributeValueMap(InventoryItem item) {
        if (item == null) return Map.of();
        Map<Long, String> result = new HashMap<>();
        itemAttributeValueRepository.findByItemId(item.getId()).forEach(v ->
                result.put(v.getAttributeDef().getId(), v.getValueText()));
        return result;
    }

    public List<InventoryItem> getItemsAssignedToMember(Long memberId) {
        if (memberId == null) return List.of();
        return inventoryItemRepository.findByAssignedMemberId(memberId);
    }

    /** Definitions exposed to filter UI (filterable flag set). */
    public List<ItemAttributeDef> findFilterableDefs(Long bandId, ItemType type) {
        Band band = bandId != null ? bandRepository.findById(bandId).orElse(null) : null;
        if (type == null) return List.of();
        if (band == null) {
            return itemAttributeDefRepository.findGlobalByItemType(type).stream()
                    .filter(ItemAttributeDef::isFilterable)
                    .toList();
        }
        return itemAttributeDefRepository.findFilterableByItemTypeAndBand(type, band);
    }
}
