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
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.application.query.inventory.InventoryAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
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
    private final BandRepository bandRepository;

    // --- Page endpoints (HTMX fragments) ---

    @GetMapping
    public String attributesPage(@RequestParam(defaultValue = "MEMBER") String type, Model model) {
        Band band = bandRepository.findById(1L).orElse(null);
        model.addAttribute("type", type);
        if (band != null) {
            model.addAttribute("attributeDefs", switch (type) {
                case "UNIFORM" -> inventoryQueryService.getUniformAttributeDefs(band);
                case "INSTRUMENT" -> inventoryQueryService.getInstrumentAttributeDefs(band);
                case "ORDER" -> inventoryQueryService.getOrderAttributeDefs(band);
                case "MEMBER" -> memberQueryService.getAttributeDefsForBand(band);
                default -> {
                    // Dla null lub innych wartości użyj atrybutów członków
                    yield memberQueryService.getAttributeDefsForBand(band);
                }
            });
        }
        return "band/inventory-attributes";
    }

    @GetMapping("/list")
    public String attributeList(Model model) {
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", memberQueryService.getAttributeDefsForBand(band));
        }
        return "band/attribute-list";
    }

    @GetMapping("/new")
    public String newAttributeForm(@RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, 0, null));
        model.addAttribute("attributeDefId", null);
        return "band/inventory-attribute-form";
    }

    @GetMapping("/{id}/edit")
    public String editAttributeForm(@PathVariable Long id, @RequestParam(defaultValue = "MEMBER") String type, Model model) {
        model.addAttribute("type", type);
        
        String name, attrType;
        boolean required;
        int displayOrder;
        String options;
        
        Object cmdService = getCommandService(type);
        if (cmdService instanceof UniformAttributeCommandService svc) {
            var def = svc.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
        } else if (cmdService instanceof InstrumentAttributeCommandService svc) {
            var def = svc.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
        } else {
            var def = memberCommandService.getAttributeDefById(id);
            name = def.getName();
            attrType = def.getType();
            required = def.isRequired();
            displayOrder = def.getDisplayOrder();
            options = def.getOptions();
        }
        
        model.addAttribute("attributeDef", new AttributeDefForm(name, attrType, required, displayOrder, options));
        model.addAttribute("attributeDefId", id);
        return "band/inventory-attribute-form";
    }

    @PostMapping
    public ResponseEntity<Void> createAttribute(@RequestParam(defaultValue = "MEMBER") String type, @ModelAttribute AttributeDefForm form) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: 1"));
        
        switch (type) {
            case "UNIFORM" -> uniformCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
            case "INSTRUMENT" -> instrumentCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
            default -> memberCommandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
        }
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes?type=" + type).build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAttribute(@PathVariable Long id, @RequestParam(defaultValue = "MEMBER") String type, @ModelAttribute AttributeDefForm form) {
        Object cmdService = getCommandService(type);
        if (cmdService instanceof UniformAttributeCommandService) {
            ((UniformAttributeCommandService) cmdService).updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
        } else if (cmdService instanceof InstrumentAttributeCommandService) {
            ((InstrumentAttributeCommandService) cmdService).updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
        } else {
            memberCommandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
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
        private int displayOrder;
        private String options; // JSON array for SELECT/MULTI_SELECT

        public AttributeDefForm() {}

        public AttributeDefForm(String name, String type, boolean required, int displayOrder, String options) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.displayOrder = displayOrder;
            this.options = options;
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
