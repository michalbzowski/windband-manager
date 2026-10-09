package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.command.inventory.DuplicateAttributeException;
import pl.michalbzowski.windband.application.command.inventory.ItemAttributeDefCommandService;
import pl.michalbzowski.windband.application.command.inventory.UnifiedInventoryItemCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.List;

/**
 * Page controller for inventory attribute definitions (F2 S1, unified {@code ItemAttributeDef}).
 * The four legacy type-specific command services (Uniform/Instrument/Order/Award) are replaced by
 * a single {@link ItemAttributeDefCommandService}; the item-type discriminator is now carried via
 * the URL {@code type} parameter and mapped to an {@link ItemType}.
 */
@Controller
@RequestMapping("/band/inventory-attributes")
@RequiredArgsConstructor
public class InventoryAttributePageController {

    private final ItemAttributeDefCommandService itemAttributeCommandService;
    private final UnifiedInventoryItemCommandService itemValueCommandService;
    private final MemberAttributeCommandService memberCommandService;
    private final UnifiedInventoryItemQueryService queryService;
    private final MemberAttributeQueryService memberQueryService;
    private final BandQueryService bandQueryService;

    private Band getActiveBand(Long activeTeamId) {
        if (activeTeamId == null) {
            return null;
        }
        return bandQueryService.getBandById(activeTeamId);
    }

    private ItemType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        String upper = type.trim().toUpperCase();
        if (upper.equals("UNIFORM")) return ItemType.UNIFORM;
        if (upper.equals("INSTRUMENT")) return ItemType.INSTRUMENT;
        if (upper.equals("AWARD")) return ItemType.AWARD;
        if (upper.equals("SHEET_MUSIC")) return ItemType.SHEET_MUSIC;
        if (upper.equals("EQUIPMENT")) return ItemType.EQUIPMENT;
        if (upper.equals("CONSUMABLE")) return ItemType.CONSUMABLE;
        if (upper.equals("FURNITURE")) return ItemType.FURNITURE;
        if (upper.equals("OTHER")) return ItemType.OTHER;
        // ORDER no longer has its own attribute set (unified model); treat as UNIFORM until S2 finalises it.
        if (upper.equals("ORDER")) return ItemType.UNIFORM;
        throw new IllegalArgumentException("Unknown type: " + type);
    }

    // === List all attribute defs (now a single list per itemType) ===

    @GetMapping
    public String listPage(@ModelAttribute("activeTeamId") Long activeTeamId,
                           @RequestParam(defaultValue = "UNIFORM") String type, Model model) {
        Band band = getActiveBand(activeTeamId);
        model.addAttribute("type", type);
        if (band == null) {
            model.addAttribute("uniformAttributeDefs", List.of());
            model.addAttribute("instrumentAttributeDefs", List.of());
            model.addAttribute("orderAttributeDefs", List.of());
            model.addAttribute("awardAttributeDefs", List.of());
            model.addAttribute("memberAttributeDefs", List.of());
            model.addAttribute("attributeDefs", List.of());
            return "band/inventory-attributes";
        }
        ItemType itemType = parseType(type);
        model.addAttribute("uniformAttributeDefs", itemType == ItemType.UNIFORM ? defsFor(band, ItemType.UNIFORM) : List.of());
        model.addAttribute("instrumentAttributeDefs", itemType == ItemType.INSTRUMENT ? defsFor(band, ItemType.INSTRUMENT) : List.of());
        model.addAttribute("orderAttributeDefs", List.of());
        model.addAttribute("awardAttributeDefs", itemType == ItemType.AWARD ? defsFor(band, ItemType.AWARD) : List.of());
        model.addAttribute("memberAttributeDefs", memberQueryService.getAttributeDefsForBand(band));
        model.addAttribute("attributeDefs", defsFor(band, itemType));
        return "band/inventory-attributes";
    }

    private List<?> defsFor(Band band, ItemType type) {
        return queryService.getAttributeDefsForType(band.getId(), type);
    }

    // === New form ===

    @GetMapping("/new")
    public String newForm(@ModelAttribute("activeTeamId") Long activeTeamId,
                          @RequestParam(defaultValue = "UNIFORM") String type, Model model) {
        Band band = getActiveBand(activeTeamId);
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "TEXT", false, false, 0, null, null, null));
        model.addAttribute("attributeDefId", null);
        model.addAttribute("availableAttributes", band != null ? defsFor(band, parseType(type)) : List.of());
        return "band/inventory-attribute-form";
    }

    // === Edit form ===

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId,
                           @RequestParam(defaultValue = "UNIFORM") String type, Model model) {
        Band band = getActiveBand(activeTeamId);
        model.addAttribute("type", type);
        model.addAttribute("availableAttributes", band != null ? defsFor(band, parseType(type)) : List.of());

        AttributeDefForm form;
        if ("MEMBER".equalsIgnoreCase(type)) {
            var def = memberCommandService.getAttributeDefById(id);
            form = new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions(), null, null);
        } else {
            var def = itemAttributeCommandService.getAttributeDefById(id);
            form = new AttributeDefForm(def.getName(),
                    def.getDataType().name(),
                    def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(),
                    def.getOptions(), def.getDependsOnDefId(), def.getConditionalValue());
        }
        model.addAttribute("attributeDef", form);
        model.addAttribute("attributeDefId", id);
        return "band/inventory-attribute-form";
    }

    // === Create ===

    @PostMapping
    public ResponseEntity<Void> create(@ModelAttribute("activeTeamId") Long activeTeamId,
                                       @RequestParam String inventoryType,
                                       @ModelAttribute AttributeDefForm form) {
        Band band = getActiveBand(activeTeamId);
        if (band == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        try {
            if ("MEMBER".equalsIgnoreCase(inventoryType)) {
                memberCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
            } else {
                itemAttributeCommandService.createAttributeDef(band, parseType(inventoryType), form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            }
            return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + inventoryType).build();
        } catch (DuplicateAttributeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    // === Update ===

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id,
                                       @RequestParam(defaultValue = "UNIFORM") String inventoryType,
                                       @ModelAttribute AttributeDefForm form) {
        if ("MEMBER".equalsIgnoreCase(inventoryType)) {
            memberCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
        } else {
            itemAttributeCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + inventoryType).build();
    }

    // === Delete ===

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(defaultValue = "UNIFORM") String type) {
        if ("MEMBER".equalsIgnoreCase(type)) {
            memberCommandService.deleteAttributeDef(id);
        } else {
            itemAttributeCommandService.deleteAttributeDef(id);
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + type).build();
    }

    // === Save attribute value for specific entity (single endpoint, type is implicit in def scope) ===

    @PostMapping("/{defId}/uniforms/{itemId}")
    public ResponseEntity<Void> saveUniformAttributeValue(@PathVariable Long defId, @PathVariable Long itemId, @RequestBody SaveValueRequest request) {
        itemValueCommandService.saveValue(itemId, defId, request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{defId}/instruments/{itemId}")
    public ResponseEntity<Void> saveInstrumentAttributeValue(@PathVariable Long defId, @PathVariable Long itemId, @RequestBody SaveValueRequest request) {
        itemValueCommandService.saveValue(itemId, defId, request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{defId}/orders/{orderId}")
    public ResponseEntity<Void> saveOrderAttributeValue(@PathVariable Long defId, @PathVariable Long orderId, @RequestBody SaveValueRequest request) {
        // Legacy order attribute slot is re-anchored onto items in S2/S3; keep the route compiling for now.
        itemValueCommandService.saveValue(orderId, defId, request.value());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class AttributeDefForm {
        private String name;
        private String attributeType;
        private boolean required;
        private boolean displayInList;
        private int displayOrder;
        private String options;
        private Long dependsOnAttributeId;
        private String dependsOnValue;

        public AttributeDefForm() {}

        public AttributeDefForm(String name, String attributeType, boolean required, boolean displayInList,
                                int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
            this.name = name;
            this.attributeType = attributeType;
            this.required = required;
            this.displayInList = displayInList;
            this.displayOrder = displayOrder;
            this.options = options;
            this.dependsOnAttributeId = dependsOnAttributeId;
            this.dependsOnValue = dependsOnValue;
        }

        public String getType() {
            return attributeType;
        }
    }

    public record SaveValueRequest(String value) {}
}
