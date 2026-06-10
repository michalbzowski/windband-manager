package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.command.inventory.*;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;

/**
 * Page controller for inventory attribute definitions (Uniform, Instrument, Order).
 * Handles list, new form, edit form, create, update, delete.
 */
@Controller
@RequestMapping("/band/inventory-attributes")
@RequiredArgsConstructor
public class InventoryAttributePageController {

    private final UniformAttributeCommandService uniformCommandService;
    private final InstrumentAttributeCommandService instrumentCommandService;
    private final OrderAttributeCommandService orderCommandService;
    private final AwardAttributeCommandService awardCommandService;
    private final MemberAttributeCommandService memberCommandService;
    private final InventoryAttributeQueryService queryService;
    private final MemberAttributeQueryService memberQueryService;
    private final BandQueryService bandQueryService;

    private Band getDefaultBand() {
        return bandQueryService.getDefaultBand();
    }

    // === List all attribute defs by type ===

    @GetMapping
    public String listPage(@RequestParam(defaultValue = "UNIFORM") String type, Model model) {
        Band band = getDefaultBand();
        model.addAttribute("type", type);
        // Provide each list under its own name so the inventory-attributes.html template
        // (which iterates per-type: ${uniformAttributeDefs}, ${instrumentAttributeDefs},
        // ${orderAttributeDefs}, ${awardAttributeDefs}, ${memberAttributeDefs}) can render.
        // Also keep `attributeDefs` for backwards compatibility with the legacy band/attribute-defs.html.
        model.addAttribute("uniformAttributeDefs", queryService.getUniformAttributeDefs(band));
        model.addAttribute("instrumentAttributeDefs", queryService.getInstrumentAttributeDefs(band));
        model.addAttribute("orderAttributeDefs", queryService.getOrderAttributeDefs(band));
        model.addAttribute("awardAttributeDefs", queryService.getAwardAttributeDefs(band));
        model.addAttribute("memberAttributeDefs", memberQueryService.getAttributeDefsForBand(band));
        model.addAttribute("attributeDefs", switch (type) {
            case "UNIFORM" -> queryService.getUniformAttributeDefs(band);
            case "INSTRUMENT" -> queryService.getInstrumentAttributeDefs(band);
            case "ORDER" -> queryService.getOrderAttributeDefs(band);
            case "AWARD" -> queryService.getAwardAttributeDefs(band);
            default -> List.of();
        });
        return "band/inventory-attributes";
    }

    // === New form ===

    @GetMapping("/new")
    public String newForm(@RequestParam String type, Model model) {
        Band band = getDefaultBand();
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, false, 0, null, null, null));
        model.addAttribute("attributeDefId", null);
        model.addAttribute("availableAttributes", getAttributeDefsList(type, band));
        return "band/inventory-attribute-form";
    }

    // === Edit form ===

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @RequestParam String type, Model model) {
        Band band = getDefaultBand();
        model.addAttribute("type", type);
        model.addAttribute("availableAttributes", getAttributeDefsList(type, band));
        AttributeDefForm form = switch (type) {
            case "UNIFORM" -> {
                var def = uniformCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions(), def.getDependsOnAttributeId(), def.getDependsOnValue());
            }
            case "INSTRUMENT" -> {
                var def = instrumentCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions(), def.getDependsOnAttributeId(), def.getDependsOnValue());
            }
            case "ORDER" -> {
                var def = orderCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions(), def.getDependsOnAttributeId(), def.getDependsOnValue());
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
        model.addAttribute("attributeDef", form);
        model.addAttribute("attributeDefId", id);
        return "band/inventory-attribute-form";
    }

    private List<?> getAttributeDefsList(String type, Band band) {
        return switch (type) {
            case "UNIFORM" -> queryService.getUniformAttributeDefs(band);
            case "INSTRUMENT" -> queryService.getInstrumentAttributeDefs(band);
            case "ORDER" -> queryService.getOrderAttributeDefs(band);
            default -> List.of();
        };
    }

    // === Create ===

    @PostMapping
    public ResponseEntity<Void> create(@RequestParam String inventoryType, @ModelAttribute AttributeDefForm form) {
        Band band = getDefaultBand();
        try {
            switch (inventoryType) {
                case "UNIFORM" -> uniformCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
                case "INSTRUMENT" -> instrumentCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
                case "ORDER" -> orderCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
                case "AWARD" -> awardCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
                case "MEMBER" -> memberCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
                default -> throw new IllegalArgumentException("Unknown inventoryType: " + inventoryType);
            }
            return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + inventoryType).build();
        } catch (DuplicateAttributeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }
    }

    // === Update ===

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestParam String inventoryType, @ModelAttribute AttributeDefForm form) {
        switch (inventoryType) {
            case "UNIFORM" -> uniformCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            case "INSTRUMENT" -> instrumentCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            case "ORDER" -> orderCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            default -> throw new IllegalArgumentException("Unknown inventoryType: " + inventoryType);
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + inventoryType).build();
    }

    // === Delete ===

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam String type) {
        switch (type) {
            case "UNIFORM" -> uniformCommandService.deleteAttributeDef(id);
            case "INSTRUMENT" -> instrumentCommandService.deleteAttributeDef(id);
            case "ORDER" -> orderCommandService.deleteAttributeDef(id);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + type).build();
    }

    // === Save attribute value for specific entity ===

    @PostMapping("/{defId}/uniforms/{itemId}")
    public ResponseEntity<Void> saveUniformAttributeValue(@PathVariable Long defId, @PathVariable Long itemId, @RequestBody SaveValueRequest request) {
        uniformCommandService.setAttributeValue(itemId, defId, request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{defId}/instruments/{itemId}")
    public ResponseEntity<Void> saveInstrumentAttributeValue(@PathVariable Long defId, @PathVariable Long itemId, @RequestBody SaveValueRequest request) {
        instrumentCommandService.setAttributeValue(itemId, defId, request.value());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{defId}/orders/{orderId}")
    public ResponseEntity<Void> saveOrderAttributeValue(@PathVariable Long defId, @PathVariable Long orderId, @RequestBody SaveValueRequest request) {
        orderCommandService.setAttributeValue(orderId, defId, request.value());
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

        public AttributeDefForm(String name, String attributeType, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
            this.name = name;
            this.attributeType = attributeType;
            this.required = required;
            this.displayInList = displayInList;
            this.displayOrder = displayOrder;
            this.options = options;
            this.dependsOnAttributeId = dependsOnAttributeId;
            this.dependsOnValue = dependsOnValue;
        }
    }

    public record SaveValueRequest(String value) {}
}
