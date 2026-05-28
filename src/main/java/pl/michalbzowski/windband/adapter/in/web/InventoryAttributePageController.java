package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.inventory.*;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

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
    private final InventoryAttributeQueryService queryService;
    private final BandRepository bandRepository;

    private Band getDefaultBand() {
        return bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band (id=1) not found"));
    }

    // === List all attribute defs by type ===

    @GetMapping
    public String listPage(@RequestParam(defaultValue = "UNIFORM") String type, Model model) {
        Band band = getDefaultBand();
        model.addAttribute("type", type);
        model.addAttribute("attributeDefs", switch (type) {
            case "UNIFORM" -> queryService.getUniformAttributeDefs(band);
            case "INSTRUMENT" -> queryService.getInstrumentAttributeDefs(band);
            case "ORDER" -> queryService.getOrderAttributeDefs(band);
            default -> List.of();
        });
        return "band/inventory-attributes";
    }

    // === New form ===

    @GetMapping("/new")
    public String newForm(@RequestParam String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, false, 0, null));
        model.addAttribute("attributeDefId", null);
        return "band/inventory-attribute-form";
    }

    // === Edit form ===

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @RequestParam String type, Model model) {
        model.addAttribute("type", type);
        AttributeDefForm form = switch (type) {
            case "UNIFORM" -> {
                var def = uniformCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions());
            }
            case "INSTRUMENT" -> {
                var def = instrumentCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions());
            }
            case "ORDER" -> {
                var def = orderCommandService.getAttributeDefById(id);
                yield new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.isDisplayInList(), def.getDisplayOrder(), def.getOptions());
            }
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
        model.addAttribute("attributeDef", form);
        model.addAttribute("attributeDefId", id);
        return "band/inventory-attribute-form";
    }

    // === Create ===

    @PostMapping
    public ResponseEntity<Void> create(@RequestParam String inventoryType, @ModelAttribute AttributeDefForm form) {
        Band band = getDefaultBand();
        switch (inventoryType) {
            case "UNIFORM" -> uniformCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
            case "INSTRUMENT" -> instrumentCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
            case "ORDER" -> orderCommandService.createAttributeDef(band, form.getName(), form.getAttributeType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
            default -> throw new IllegalArgumentException("Unknown inventoryType: " + inventoryType);
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/inventory-attributes?type=" + inventoryType).build();
    }

    // === Update ===

    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable Long id, @RequestParam String inventoryType, @ModelAttribute AttributeDefForm form) {
        switch (inventoryType) {
            case "UNIFORM" -> uniformCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
            case "INSTRUMENT" -> instrumentCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
            case "ORDER" -> orderCommandService.updateAttributeDef(id, form.getName(), form.getAttributeType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
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

        public AttributeDefForm() {}

        public AttributeDefForm(String name, String attributeType, boolean required, boolean displayInList, int displayOrder, String options) {
            this.name = name;
            this.attributeType = attributeType;
            this.required = required;
            this.displayInList = displayInList;
            this.displayOrder = displayOrder;
            this.options = options;
        }
    }

    public record SaveValueRequest(String value) {}
}
