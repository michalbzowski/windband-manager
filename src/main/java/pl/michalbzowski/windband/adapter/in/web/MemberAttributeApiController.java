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
public class MemberAttributeApiController {

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
                request.isRequired(), request.getDisplayOrder(), request.getOptions());
        return ResponseEntity.status(HttpStatus.CREATED).body(def);
    }

    @PutMapping("/{id}")
    public MemberAttributeDef updateAttributeDef(@PathVariable Long id,
                                                  @RequestBody AttributeDefRequest request) {
        return commandService.updateAttributeDef(id, request.getName(), request.getType(),
                request.isRequired(), request.getDisplayOrder(), request.getOptions());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttributeDef(@PathVariable Long id) {
        commandService.deleteAttributeDef(id);
        return ResponseEntity.noContent().build();
    }

    // --- Attribute values per member ---

    @PostMapping("/{attrId}/members/{memberId}")
    public ResponseEntity<Void> setAttributeValue(@PathVariable Long attrId,
                                                   @PathVariable Long memberId,
                                                   @RequestBody AttributeValueRequest request) {
        commandService.setAttributeValue(memberId, attrId, request.getValue());
        return ResponseEntity.ok().build();
    }

    @Data
    public static class AttributeValueRequest {
        private String value;
    }

    @Data
    public static class AttributeDefRequest {
        private String name;
        private String type = "BOOLEAN";
        private boolean required;
        private int displayOrder;
        private String options;
    }
}
