package pl.michalbzowski.windband.application.command.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.AttributeDataType;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDef;
import pl.michalbzowski.windband.domain.inventory.ItemAttributeDefRepository;
import pl.michalbzowski.windband.domain.inventory.ItemType;

/**
 * Unified CRUD service for {@link ItemAttributeDef} definitions (F2 step 1).
 * Replaces the four legacy *AttributeCommandService classes (Uniform, Instrument,
 * Order, Award) which each carried a per-type create/update/delete/getById surface.
 *
 * Type-specific attribute names (e.g. dependsOnAttributeId) are flattened onto the
 * definition's {@code dependsOnDefId}/{@code conditionalValue} fields; the web-facing
 * form mapping is finalised in S2.
 */
@Service
@RequiredArgsConstructor
public class ItemAttributeDefCommandService {

    private final ItemAttributeDefRepository itemAttributeDefRepository;

    @Transactional
    public ItemAttributeDef createAttributeDef(Band band, ItemType itemType, String name, String attributeType,
                                               boolean required, boolean displayInList, int displayOrder,
                                               String options, Long dependsOnAttributeId,
                                               String dependsOnValue) {
        AttributeDataType dataType = parseDataType(attributeType);
        ItemAttributeDef def = (band != null)
                ? ItemAttributeDef.createBandScoped(name, dataType, itemType, band)
                : ItemAttributeDef.createGlobal(name, dataType, itemType);
        def.setRequired(required);
        def.setDisplayInList(displayInList);
        def.setDisplayOrder(displayOrder);
        if (options != null && !options.isBlank()) {
            def.setOptions(options);
        }
        if (dependsOnAttributeId != null) {
            def.setDependsOnDefId(dependsOnAttributeId);
        }
        if (dependsOnValue != null && !dependsOnValue.isBlank()) {
            def.setConditionalValue(dependsOnValue);
        }
        if (itemAttributeDefRepository.existsByNameAndItemTypeAndBand(name, itemType, band)) {
            throw new DuplicateAttributeException("Attribute '" + name + "' already exists for this type");
        }
        return itemAttributeDefRepository.save(def);
    }

    @Transactional
    public ItemAttributeDef updateAttributeDef(Long id, String name, String attributeType, boolean required,
                                               boolean displayInList, int displayOrder, String options,
                                               Long dependsOnAttributeId, String dependsOnValue) {
        ItemAttributeDef def = itemAttributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item attribute definition not found: " + id));
        def.setName(name);
        def.setDataType(parseDataType(attributeType));
        def.setRequired(required);
        def.setDisplayInList(displayInList);
        def.setDisplayOrder(displayOrder);
        if (options == null || options.isBlank()) {
            def.setOptions(null);
        } else {
            def.setOptions(options);
        }
        def.setDependsOnDefId(dependsOnAttributeId);
        def.setConditionalValue(dependsOnValue != null && !dependsOnValue.isBlank() ? dependsOnValue : null);
        return itemAttributeDefRepository.save(def);
    }

    @Transactional
    public void deleteAttributeDef(Long id) {
        ItemAttributeDef def = itemAttributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item attribute definition not found: " + id));
        itemAttributeDefRepository.delete(def);
    }

    public ItemAttributeDef getAttributeDefById(Long id) {
        return itemAttributeDefRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item attribute definition not found: " + id));
    }

    // === Helpers ===

    private AttributeDataType parseDataType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("attributeType is required");
        }
        try {
            return AttributeDataType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown attribute data type: " + raw);
        }
    }
}
