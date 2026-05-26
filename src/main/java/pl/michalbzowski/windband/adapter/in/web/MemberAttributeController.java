package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

import java.util.List;

@Controller
@RequestMapping("/band/attributes")
@RequiredArgsConstructor
public class MemberAttributeController {

    private final MemberAttributeCommandService commandService;
    private final MemberAttributeQueryService queryService;
    private final BandRepository bandRepository;

    // --- Page endpoints (HTMX fragments) ---

    @GetMapping
    public String attributesPage(Model model) {
        return "band/attribute-defs";
    }

    @GetMapping("/list")
    public String attributeList(Model model) {
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", queryService.getAttributeDefsForBand(band));
        }
        return "band/attribute-list";
    }

    @GetMapping("/new")
    public String newAttributeForm(Model model) {
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, 0));
        model.addAttribute("attributeDefId", null);
        return "band/attribute-form";
    }

    @GetMapping("/{id}/edit")
    public String editAttributeForm(@PathVariable Long id, Model model) {
        MemberAttributeDef def = commandService.getAttributeDefById(id);
        model.addAttribute("attributeDef", new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.getDisplayOrder()));
        model.addAttribute("attributeDefId", id);
        return "band/attribute-form";
    }

    @PostMapping
    public String createAttribute(@ModelAttribute AttributeDefForm form, Model model) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: 1"));
        commandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder());
        // Return updated list for HTMX
        model.addAttribute("attributeDefs", queryService.getAttributeDefsForBand(band));
        return "band/attribute-list";
    }

    @PutMapping("/{id}")
    public String updateAttribute(@PathVariable Long id, @ModelAttribute AttributeDefForm form, Model model) {
        commandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder());
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", queryService.getAttributeDefsForBand(band));
        }
        return "band/attribute-list";
    }

    @DeleteMapping("/{id}")
    public String deleteAttribute(@PathVariable Long id, Model model) {
        commandService.deleteAttributeDef(id);
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", queryService.getAttributeDefsForBand(band));
        }
        return "band/attribute-list";
    }

    @Data
    public static class AttributeDefForm {
        private String name;
        private String type;
        private boolean required;
        private int displayOrder;

        public AttributeDefForm() {}

        public AttributeDefForm(String name, String type, boolean required, int displayOrder) {
            this.name = name;
            this.type = type;
            this.required = required;
            this.displayOrder = displayOrder;
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
