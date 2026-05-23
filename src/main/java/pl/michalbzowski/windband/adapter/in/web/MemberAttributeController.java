package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.band.MemberAttributeCommandService;
import pl.michalbzowski.windband.application.query.band.MemberAttributeQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.band.MemberAttributeDef;

import java.util.List;

@RestController
@RequestMapping("/api/bands/{bandId}/attribute-defs")
@RequiredArgsConstructor
public class MemberAttributeController {

    private final MemberAttributeCommandService commandService;
    private final MemberAttributeQueryService queryService;
    private final BandRepository bandRepository;

    @GetMapping
    public List<MemberAttributeDef> getAttributeDefs(@PathVariable Long bandId) {
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));
        return queryService.getAttributeDefsForBand(band);
    }

    @PostMapping
    public ResponseEntity<MemberAttributeDef> createAttributeDef(@PathVariable Long bandId,
                                                                  @RequestBody AttributeDefRequest request) {
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalArgumentException("Band not found: " + bandId));
        var def = commandService.createAttributeDef(band, request.getName(), request.getType(),
                request.isRequired(), request.getDisplayOrder());
        return ResponseEntity.status(HttpStatus.CREATED).body(def);
    }

    @PutMapping("/{id}")
    public MemberAttributeDef updateAttributeDef(@PathVariable Long id,
                                                  @RequestBody AttributeDefRequest request) {
        return commandService.updateAttributeDef(id, request.getName(), request.getType(),
                request.isRequired(), request.getDisplayOrder());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttributeDef(@PathVariable Long id) {
        commandService.deleteAttributeDef(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{defId}/members/{memberId}")
    public ResponseEntity<Void> setAttributeValue(@PathVariable Long memberId,
                                                   @PathVariable Long defId,
                                                   @RequestBody AttributeValueRequest request) {
        commandService.setAttributeValue(memberId, defId, request.getValue());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{defId}/members/{memberId}")
    public ResponseEntity<Void> deleteAttributeValue(@PathVariable Long memberId,
                                                      @PathVariable Long defId) {
        commandService.deleteAttributeValue(memberId, defId);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class AttributeDefRequest {
        private String name;
        private String type = "BOOLEAN";
        private boolean required;
        private int displayOrder;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public int getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    }

    @Data
    public static class AttributeValueRequest {
        private String value;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
