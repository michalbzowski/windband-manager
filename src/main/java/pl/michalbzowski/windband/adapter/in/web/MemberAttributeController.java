package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        Band band = bandRepository.findById(1L).orElse(null);
        if (band != null) {
            model.addAttribute("attributeDefs", queryService.getAttributeDefsForBand(band));
        }
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
        model.addAttribute("attributeDef", new AttributeDefForm("", "BOOLEAN", false, 0, null));
        model.addAttribute("attributeDefId", null);
        return "band/attribute-form";
    }

    @GetMapping("/{id}/edit")
    public String editAttributeForm(@PathVariable Long id, Model model) {
        MemberAttributeDef def = commandService.getAttributeDefById(id);
        model.addAttribute("attributeDef", new AttributeDefForm(def.getName(), def.getType(), def.isRequired(), def.getDisplayOrder(), def.getOptions()));
        model.addAttribute("attributeDefId", id);
        return "band/attribute-edit";
    }

    @PostMapping
    public ResponseEntity<Void> createAttribute(@ModelAttribute AttributeDefForm form) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: 1"));
        commandService.createAttributeDef(band, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes").build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAttribute(@PathVariable Long id, @ModelAttribute AttributeDefForm form) {
        commandService.updateAttributeDef(id, form.getName(), form.getType(), form.isRequired(), form.getDisplayOrder(), form.getOptions());
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes").build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttribute(@PathVariable Long id) {
        commandService.deleteAttributeDef(id);
        return ResponseEntity.ok().header("HX-Redirect", "/band/attributes").build();
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
}
