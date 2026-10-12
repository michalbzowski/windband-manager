package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.command.inventory.ItemAttributeDefCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;

/**
 * Band attribute management (F2 S1): member attributes keep their legacy endpoints,
 * inventory item attributes are served through the unified {@code ItemAttributeDef}
 * model and {@link ItemAttributeDefCommandService}.
 */
@Controller
@RequestMapping("/band/attributes")
@RequiredArgsConstructor
public class MemberAttributeController {

    private final MemberAttributeCommandService memberCommandService;
    private final ItemAttributeDefCommandService itemAttributeCommandService;
    private final MemberAttributeQueryService memberQueryService;
    private final UnifiedInventoryItemQueryService inventoryQueryService;
    private final BandQueryService bandQueryService;

    private Band getActiveBand(Long activeTeamId) {
        if (activeTeamId == null) {
            return null;
        }
        return bandQueryService.getBandById(activeTeamId);
    }

    // --- Page endpoints (HTMX fragments) ---

    @GetMapping
    public String attributesPage(@ModelAttribute("activeTeamId") Long activeTeamId,
                                 @RequestParam(defaultValue = "MEMBER") String type, Model model) {
        return "redirect:/band/inventory-attributes?type=" + type;
    }

    @GetMapping("/list")
    public String attributeList(@ModelAttribute("activeTeamId") Long activeTeamId, Model model) {
        Band band = getActiveBand(activeTeamId);
        if (band == null) {
            model.addAttribute("attributeDefs", List.of());
            return "band/attribute-list";
        }
        model.addAttribute("attributeDefs", memberQueryService.getAttributeDefsForBand(band));
        return "band/attribute-list";
    }

    @GetMapping("/new")
    public String newAttributeForm(@ModelAttribute("activeTeamId") Long activeTeamId,
                                   @RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, false, 0, null, null, null));
        model.addAttribute("attributeDefId", null);
        model.addAttribute("availableAttributes", getAvailableAttributes(activeTeamId, type));
        return "band/inventory-attribute-form";
    }

    private List<?> getAvailableAttributes(Long activeTeamId, String type) {
        Band band = getActiveBand(activeTeamId);
        if (band == null) {
            return List.of();
        }
        switch (type) {
            case "UNIFORM" -> { return inventoryQueryService.getAttributeDefsForType(band.getId(), pl.michalbzowski.windband.domain.inventory.ItemType.UNIFORM); }
            case "INSTRUMENT" -> { return inventoryQueryService.getAttributeDefsForType(band.getId(), pl.michalbzowski.windband.domain.inventory.ItemType.INSTRUMENT); }
            case "AWARD" -> { return inventoryQueryService.getAttributeDefsForType(band.getId(), pl.michalbzowski.windband.domain.inventory.ItemType.AWARD); }
            default -> { return memberQueryService.getAttributeDefsForBand(band); }
        }
    }

    @GetMapping("/{id}/edit")
    public String editAttributeForm(@PathVariable Long id, @ModelAttribute("activeTeamId") Long activeTeamId,
                                    @RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("availableAttributes", getAvailableAttributes(activeTeamId, type));

        String name;
        String attrType;
        boolean required;
        boolean displayInList = false;
        int displayOrder = 0;
        String options = null;
        Long dependsOnAttributeId = null;
        String dependsOnValue = null;

        if ("UNIFORM".equals(type) || "INSTRUMENT".equals(type) || "AWARD".equals(type)) {
            var def = itemAttributeCommandService.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getDataType().name();
            required = def.isRequired();
            displayInList = def.isDisplayInList();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
            dependsOnAttributeId = def.getDependsOnDefId();
            dependsOnValue = def.getConditionalValue();
        } else {
            var def = memberCommandService.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayInList = def.isDisplayInList();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
        }

        model.addAttribute("attributeDef", new AttributeDefForm(name, attrType, required, displayInList, displayOrder, options, dependsOnAttributeId, dependsOnValue));
        model.addAttribute("attributeDefId", id);
        return "band/inventory-attribute-form";
    }

    @PostMapping
    public ResponseEntity<Void> createAttribute(@ModelAttribute("activeTeamId") Long activeTeamId,
                                                @RequestParam(defaultValue = "MEMBER") String type,
                                                @ModelAttribute AttributeDefForm form) {
        Band band = getActiveBand(activeTeamId);
        if (band == null) {
            return ResponseEntity.badRequest().build();
        }

        switch (type) {
            case "UNIFORM" -> itemAttributeCommandService.createAttributeDef(band, pl.michalbzowski.windband.domain.inventory.ItemType.UNIFORM, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            case "INSTRUMENT" -> itemAttributeCommandService.createAttributeDef(band, pl.michalbzowski.windband.domain.inventory.ItemType.INSTRUMENT, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            case "AWARD" -> itemAttributeCommandService.createAttributeDef(band, pl.michalbzowski.windband.domain.inventory.ItemType.AWARD, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            default -> memberCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAttribute(@PathVariable Long id,
                                                @RequestParam(defaultValue = "MEMBER") String type,
                                                @ModelAttribute AttributeDefForm form) {
        switch (type) {
            case "UNIFORM", "INSTRUMENT", "AWARD" -> itemAttributeCommandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            default -> memberCommandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable Long id,
                                                @RequestParam(defaultValue = "MEMBER") String type) {
        switch (type) {
            case "UNIFORM", "INSTRUMENT", "AWARD" -> itemAttributeCommandService.deleteAttributeDef(id);
            default -> memberCommandService.deleteAttributeDef(id);
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @Data
    public static class AttributeDefForm {
        private String name;
        private String type;
        private boolean required;
        private boolean displayInList;
        private int displayOrder;
        private String options;
        private Long dependsOnAttributeId;
        private String dependsOnValue;

        public AttributeDefForm() {}

        public AttributeDefForm(String name, String type, boolean required, boolean displayInList, int displayOrder,
                                String options, Long dependsOnAttributeId, String dependsOnValue) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.displayInList = displayInList;
            this.displayOrder = displayOrder;
            this.options = options;
            this.dependsOnAttributeId = dependsOnAttributeId;
            this.dependsOnValue = dependsOnValue;
        }
    }

    @Data
    public static class AttributeDefRequest {
        private String name;
        private String type = "BOOLEAN";
        private boolean required;
        private int displayOrder;
    }
}
