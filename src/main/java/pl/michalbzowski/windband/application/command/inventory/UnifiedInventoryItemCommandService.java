package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.InventoryItem;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDefRepository;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValue;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeValueRepository;
import pl.michalbzowski.windband.domain.inventory.ItemCondition;
import pl.michalbzowski.windband.domain.inventory.InventoryItemRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified write service for the single concrete {@link InventoryItem} model (F2 step 1).
 * Replaces InventoryCommandService and the four *AttributeCommandService (legacy).
 *
 * Note: assignment-history persistence is re-anchored under InventoryItem in F2 step 3 (S3);
 * until then assign/return transitions operate on the item itself.
 */
@Service
@RequiredArgsConstructor
public class UnifiedInventoryItemCommandService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemAttributeDefRepository itemAttributeDefRepository;
    private final ItemAttributeValueRepository itemAttributeValueRepository;
    private final MemberRepository memberRepository;
    private final pl.michalbzowski.windband.domain.band.BandRepository bandRepository;

    @Transactional
    public InventoryItem assignItem(Long itemId, Long memberId, ItemCondition conditionAtAssign, String notes) {
        InventoryItem item = getItemOrThrow(itemId);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        // Close any previous assignment first (unassign -> AVAILABLE), then assign.
        if (item.isAssigned()) {
            closeAssignment(item);
        }
        item.assignTo(member);
        if (conditionAtAssign != null) {
            item.setCondition(conditionAtAssign);
        }
        if (notes != null && !notes.isBlank()) {
            item.setNotes(notes);
        }
        return inventoryItemRepository.save(item);
    }

    @Transactional
    public InventoryItem returnItem(Long itemId, ItemCondition conditionAtReturn, String notes) {
        InventoryItem item = getItemOrThrow(itemId);
        closeAssignment(item);
        if (conditionAtReturn != null) {
            item.setCondition(conditionAtReturn);
        }
        if (notes != null && !notes.isBlank()) {
            item.setNotes(notes);
        }
        return inventoryItemRepository.save(item);
    }

    @Transactional
    public InventoryItem retireItem(Long itemId) {
        InventoryItem item = getItemOrThrow(itemId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot retire an item that is assigned to a member. Return it first.");
        }
        item.retireFromStock();
        return inventoryItemRepository.save(item);
    }

    @Transactional
    public InventoryItem disposeItem(Long itemId) {
        InventoryItem item = getItemOrThrow(itemId);
        if (item.isAssigned()) {
            throw new IllegalStateException("Cannot dispose an item that is assigned to a member. Return it first.");
        }
        item.dispose();
        return inventoryItemRepository.save(item);
    }

    /**
     * Upserts ItemAttributeValue rows for the given defIds (typed via the def's dataType,
     * parsed by the {@code new ItemAttributeValue(item, def, valueText)} contract).
     * Defs in the item's active set (global + band-scoped) that are absent from the
     * incoming map have their values removed; explicit blank values also clear.
     */
    @Transactional
    public Map<Long, ItemAttributeValue> updateItemAttributes(Long itemId, Map<Long, String> values, Long bandId) {
        InventoryItem item = getItemOrThrow(itemId);
        Map<Long, String> valueMap = values != null ? values : Map.of();

        Band band = item.getBand() != null ? item.getBand() : (bandId != null ? bandRepository.findById(bandId).orElse(null) : null);

        // Remove values for active defs not present in the incoming map.
        List<ItemAttributeDef> activeDefs = resolveActiveDefs(band, item.getType());
        Set<Long> keepIds = new HashSet<>(valueMap.keySet());
        for (ItemAttributeDef def : activeDefs) {
            if (!keepIds.contains(def.getId())) {
                itemAttributeValueRepository.findByItemIdAndAttributeDefId(itemId, def.getId())
                        .forEach(itemAttributeValueRepository::delete);
            }
        }

        Map<Long, ItemAttributeValue> saved = new HashMap<>();
        for (Map.Entry<Long, String> entry : valueMap.entrySet()) {
            Long defId = entry.getKey();
            ItemAttributeDef def = itemAttributeDefRepository.findById(defId)
                    .orElseThrow(() -> new DuplicateAttributeException("Attribute definition not found: " + defId));
            String valueText = entry.getValue();

            // An explicit null/blank clears the value for that def.
            if (valueText == null || valueText.isBlank()) {
                itemAttributeValueRepository.findByItemIdAndAttributeDefId(itemId, defId)
                        .forEach(itemAttributeValueRepository::delete);
                continue;
            }

            var existing = itemAttributeValueRepository.findByItemIdAndAttributeDef(itemId, def);
            ItemAttributeValue value;
            if (existing.isPresent()) {
                value = existing.get();
                value.setValueText(valueText); // refresh typed columns via the typing path in setValueText
            } else {
                value = new ItemAttributeValue(item, def, valueText); // typed parse from String ctor
            }
            saved.put(defId, itemAttributeValueRepository.save(value));
        }
        return saved;
    }

    /**
     * Upsert a single attribute value for an item (web-layer contract carried over from the
     * legacy *AttributeCommandService.setAttributeValue). Unlike {@link #updateItemAttributes}
     * it does NOT delete values for other defs.
     */
    @Transactional
    public ItemAttributeValue saveValue(Long itemId, Long defId, String value) {
        InventoryItem item = getItemOrThrow(itemId);
        ItemAttributeDef def = itemAttributeDefRepository.findById(defId)
                .orElseThrow(() -> new IllegalArgumentException("Item attribute definition not found: " + defId));
        List<ItemAttributeValue> existingList = itemAttributeValueRepository.findByItemIdAndAttributeDefId(itemId, defId);
        if (value == null || value.isBlank()) {
            existingList.forEach(itemAttributeValueRepository::delete);
            return null;
        }
        if (!existingList.isEmpty()) {
            ItemAttributeValue v = existingList.get(0);
            v.setValueText(value);
            return itemAttributeValueRepository.save(v);
        }
        return itemAttributeValueRepository.save(new ItemAttributeValue(item, def, value));
    }

    // === Helpers ===

    private InventoryItem getItemOrThrow(Long id) {
        if (id == null) {
            throw new InventoryItemNotFoundException(null);
        }
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    /**
     * F2/S3 will record assignment-history here under the re-anchored InventoryItem.
     */
    private void closeAssignment(InventoryItem item) {
        item.unassign();
    }

    private List<ItemAttributeDef> resolveActiveDefs(Band band, pl.michalbzowski.windband.domain.inventory.ItemType type) {
        if (type == null) return List.of();
        if (band == null) {
            return itemAttributeDefRepository.findGlobalByItemType(type);
        }
        return itemAttributeDefRepository.findActiveByItemTypeAndBand(type, band);
    }
}
