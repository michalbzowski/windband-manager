package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.command.inventory.InstrumentAttributeCommandService;
import pl.michalbzowski.windband.application.command.inventory.UniformAttributeCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

import java.util.List;

@Controller
@RequestMapping("/band/attributes")
@RequiredArgsConstructor
public class MemberAttributeController {

    private final MemberAttributeCommandService memberCommandService;
    private final UniformAttributeCommandService uniformCommandService;
    private final InstrumentAttributeCommandService instrumentCommandService;
    private final MemberAttributeQueryService memberQueryService;
    private final InventoryAttributeQueryService inventoryQueryService;
    private final BandQueryService bandQueryService;

    // --- Page endpoints (HTMX fragments) ---

    @GetMapping
    public String attributesPage(@RequestParam(defaultValue = "MEMBER") String type, Model model) {
        Band band = bandQueryService.getDefaultBand();
        model.addAttribute("type", type);
        model.addAttribute("attributeDefs", switch (type) {
            case "UNIFORM" -> inventoryQueryService.getUniformAttributeDefs(band);
            case "INSTRUMENT" -> inventoryQueryService.getInstrumentAttributeDefs(band);
            case "ORDER" -> inventoryQueryService.getOrderAttributeDefs(band);
            case "MEMBER" -> memberQueryService.getAttributeDefsForBand(band);
            default -> memberQueryService.getAttributeDefsForBand(band);
        });
        return "band/inventory-attributes";
    }

    @GetMapping("/list")
    public String attributeList(Model model) {
        Band band = bandQueryService.getDefaultBand();
        model.addAttribute("attributeDefs", memberQueryService.getAttributeDefsForBand(band));
        return "band/attribute-list";
    }

    @GetMapping("/new")
    public String newAttributeForm(@RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, false, 0, null, null, null));
        model.addAttribute("attributeDefId", null);
        model.addAttribute("availableAttributes", getAvailableAttributes(type));
        return "band/inventory-attribute-form";
    }

    private List<?> getAvailableAttributes(String type) {
        Band band = bandQueryService.getDefaultBand();
        return switch (type) {
            case "UNIFORM" -> inventoryQueryService.getUniformAttributeDefs(band);
            case "INSTRUMENT" -> inventoryQueryService.getInstrumentAttributeDefs(band);
            default -> memberQueryService.getAttributeDefsForBand(band);
        };
    }

    @GetMapping("/{id}/edit")
    public String editAttributeForm(@PathVariable Long id, @RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("availableAttributes", getAvailableAttributes(type));
        
        String name, attrType;
        boolean required;
        boolean displayInList = false;
        int displayOrder = 0;
        String options = null;
        Long dependsOnAttributeId = null;
        String dependsOnValue = null;
        
        Object cmdService = getCommandService(type);
        if (cmdService instanceof UniformAttributeCommandService svc) {
            var def = svc.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayInList = def.isDisplayInList();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
            dependsOnAttributeId = def.getDependsOnAttributeId();
            dependsOnValue = def.getDependsOnValue();
        } else if (cmdService instanceof InstrumentAttributeCommandService svc) {
            var def = svc.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayInList = def.isDisplayInList();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
            dependsOnAttributeId = def.getDependsOnAttributeId();
            dependsOnValue = def.getDependsOnValue();
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
    public ResponseEntity<Void> createAttribute(@RequestParam(defaultValue = "MEMBER") String type, @ModelAttribute AttributeDefForm form) {
        Band band = bandQueryService.getDefaultBand();
        
        switch (type) {
            case "UNIFORM" -> uniformCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            case "INSTRUMENT" -> instrumentCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
            default -> memberCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAttribute(@PathVariable Long id, @RequestParam(defaultValue = "MEMBER") String type, @ModelAttribute AttributeDefForm form) {
        Object cmdService = getCommandService(type);
        if (cmdService instanceof UniformAttributeCommandService) {
            ((UniformAttributeCommandService) cmdService).updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
        } else if (cmdService instanceof InstrumentAttributeCommandService) {
            ((InstrumentAttributeCommandService) cmdService).updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions(), form.getDependsOnAttributeId(), form.getDependsOnValue());
        } else {
            memberCommandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.isDisplayInList(), form.getDisplayOrder(), form.getOptions());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable Long id, @RequestParam(defaultValue = "MEMBER") String type) {
        Object cmdService = getCommandService(type);
        if (cmdService instanceof UniformAttributeCommandService) {
            ((UniformAttributeCommandService) cmdService).deleteAttributeDef(id);
        } else if (cmdService instanceof InstrumentAttributeCommandService) {
            ((InstrumentAttributeCommandService) cmdService).deleteAttributeDef(id);
        } else {
            memberCommandService.deleteAttributeDef(id);
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

        public AttributeDefForm(String name, String type, boolean required, boolean displayInList, int displayOrder, String options, Long dependsOnAttributeId, String dependsOnValue) {
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

    private Object getCommandService(String type) {
        return switch (type) {
            case "UNIFORM" -> uniformCommandService;
            case "INSTRUMENT" -> instrumentCommandService;
            default -> memberCommandService;
        };
    }
}
