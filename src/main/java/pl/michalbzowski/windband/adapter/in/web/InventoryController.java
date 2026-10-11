package pl.michalbzowski.windband.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.inventory.UnifiedInventoryItemCommandService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.ItemCondition;
import pl.michalbzowski.windband.domain.inventory.ItemType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified inventory REST API (F2 S2) over the single concrete {@code InventoryItem} model.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /api/inventory/items?bandId=&type=&q=} — list items with attribute values</li>
 *   <li>{@code GET    /api/inventory/items/{id}               — detail (attributes + assigned member)</li>
 *   <li>{@code POST   /api/inventory/items                    — create (required-attribute validation, 400 on miss)</li>
 *   <li>{@code PUT    /api/inventory/items/{id}               — update basic fields</li>
 *   <li>{@code PUT    /api/inventory/items/{id}/attributes    — upsert attribute values (defId -> valueText)</li>
 *   <li>{@code POST   /api/inventory/items/{id}/assign        — assign to member</li>
 *   <li>{@code POST   /api/inventory/items/{id}/return        — return to stock</li>
 *   <li>{@code POST   /api/inventory/items/{id}/retire        — retire from stock (409 while assigned)</li>
 *   <li>{@code POST   /api/inventory/items/{id}/dispose       — dispose (409 while assigned)</li>
 *   <li>{@code GET    /api/inventory/definitions?bandId=&type=} — attribute definitions for the form/filter</li>
 * </ul>
 *
 * <p>Legacy routes ({@code /orders, /uniforms, /instruments, /awards}) from the pre-F2 model keep
 * returning {@code 410 Gone} (acceptance F2 §2.5) or — where a thin adapter already existed over the
 * unified model — serving it read-only instead.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final UnifiedInventoryItemCommandService commandService;
    private final UnifiedInventoryItemQueryService queryService;
    private final BandQueryService bandQueryService;

    // ==================== Band resolution ====================

    private Band resolveActiveBand(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu && wu.getActiveTeamId() != null) {
            return bandQueryService.getBandById(wu.getActiveTeamId());
        }
        return null;
    }

    /** Form-based session team (test profile) → principal active team (prod). */
    private Band resolveBand(Long explicitBandId, @AuthenticationPrincipal OidcUser oidcUser) {
        if (explicitBandId != null) {
            return bandQueryService.getBandById(explicitBandId);
        }
        return resolveActiveBand(oidcUser);
    }

    private Band requireBand(Long explicitBandId, @AuthenticationPrincipal OidcUser oidcUser) {
        Band band = resolveBand(explicitBandId, oidcUser);
        if (band == null) {
            throw new IllegalStateException("No active band for this request. Select a team or pass bandId.");
        }
        return band;
    }

    private ItemCondition parseCondition(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ItemCondition.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Long memberNameOf(pl.michalbzowski.windband.domain.inventory.InventoryItem item) {
        return item.getAssignedMember() != null ? item.getAssignedMember().getId() : null;
    }

    // ==================== Items (unified, F2 S2) ====================

    @GetMapping("/items")
    public ResponseEntity<List<ItemView>> listItems(
            @RequestParam(required = false) Long bandId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @AuthenticationPrincipal OidcUser oidcUser) {
        Band band = resolveBand(bandId, oidcUser);
        if (band == null) return ResponseEntity.ok(List.of());
        List<pl.michalbzowski.windband.domain.inventory.InventoryItem> items = queryService.getAllItemsByBand(band.getId());
        String typeCode = parseType(type);
        String query = q != null ? q.trim().toLowerCase() : null;
        List<ItemView> view = items.stream()
                .filter(i -> typeCode == null || i.getType().name().equalsIgnoreCase(typeCode))
                .filter(i -> query == null || query.isEmpty()
                        || matchesQuery(i, query))
                .map(this::toItemView)
                .toList();
        return ResponseEntity.ok(view);
    }

    private boolean matchesQuery(pl.michalbzowski.windband.domain.inventory.InventoryItem i, String query) {
        if ((i.getName() != null && i.getName().toLowerCase().contains(query))) return true;
        if (i.getSerialNumber() != null && i.getSerialNumber().toLowerCase().contains(query)) return true;
        if (i.getAssignedMemberName() != null && i.getAssignedMemberName().toLowerCase().contains(query)) return true;
        for (String v : queryService.getAttributeValueMap(i).values()) {
            if (v != null && v.toLowerCase().contains(query)) return true;
        }
        return false;
    }

    private String parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ItemType.valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping("/items/{id}")
    public ResponseEntity<ItemView> getItem(@PathVariable Long id,
                                            @AuthenticationPrincipal OidcUser oidcUser) {
        pl.michalbzowski.windband.domain.inventory.InventoryItem item = queryService.getItemById(id);
        return ResponseEntity.ok(toItemView(item));
    }

    private ItemView toItemView(pl.michalbzowski.windband.domain.inventory.InventoryItem item) {
        var mv = new ItemView();
        mv.id = item.getId();
        mv.type = item.getType() != null ? item.getType().name() : null;
        mv.typeDisplayName = item.getType() != null ? item.getType().getDisplayName() : null;
        mv.name = item.getName();
        mv.description = item.getDescription();
        mv.serialNumber = item.getSerialNumber();
        mv.manufacturer = item.getManufacturer();
        mv.model = item.getModel();
        mv.condition = item.getCondition() != null ? item.getCondition().name() : null;
        mv.ownershipStatus = item.getOwnershipStatus() != null ? item.getOwnershipStatus().name() : null;
        mv.lifecycleStatus = item.getLifecycleStatus() != null ? item.getLifecycleStatus().name() : null;
        mv.notes = item.getNotes();
        mv.assignedMemberId = memberNameOf(item);
        mv.assignedMemberName = item.getAssignedMemberName();
        mv.attributes = new HashMap<>(queryService.getAttributeValueMap(item));
        return mv;
    }

    @PostMapping("/items")
    public ResponseEntity<ItemView> createItem(@RequestBody CreateItemRequest request,
                                               @AuthenticationPrincipal OidcUser oidcUser) {
        Band band = requireBand(request.bandId, oidcUser);
        ItemType type;
        try {
            type = ItemType.valueOf(request.type.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown item type: " + request.type);
        }
        Map<Long, String> attrs = normalizeLongKeys(request.attributes);
        var item = commandService.createItem(band, request.name, type,
                parseCondition(request.condition),
                request.description, request.serialNumber,
                request.manufacturer, request.model,
                attrs);
        return ResponseEntity.status(HttpStatus.CREATED).body(toItemView(item));
    }

    private Map<Long, String> normalizeLongKeys(Map<String, String> raw) {
        Map<Long, String> out = new HashMap<>();
        if (raw == null) return out;
        raw.forEach((k, v) -> {
            try {
                out.put(Long.parseLong(k), v);
            } catch (NumberFormatException ignored) {
                // keys that are not attribute def ids are skipped
            }
        });
        return out;
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ItemView> updateItem(@PathVariable Long id,
                                               @RequestBody UpdateItemRequest request,
                                               @AuthenticationPrincipal OidcUser oidcUser) {
        var saved = commandService.updateItemFields(id,
                request != null ? request.name : null,
                request != null ? request.description : null,
                parseCondition(request != null ? request.condition : null),
                request != null ? request.serialNumber : null,
                request != null ? request.manufacturer : null,
                request != null ? request.model : null);
        return ResponseEntity.ok(toItemView(saved));
    }

    @PutMapping("/items/{id}/attributes")
    public ResponseEntity<Void> updateAttributes(@PathVariable Long id,
                                                 @RequestBody Map<String, String> rawAttrs,
                                                 @AuthenticationPrincipal OidcUser oidcUser) {
        commandService.updateItemAttributes(id, normalizeLongKeys(rawAttrs), null);
        return ResponseEntity.ok().build();
    }

    // ==================== Lifecycle transitions ====================

    @PostMapping("/items/{id}/assign")
    public ResponseEntity<Void> assign(@PathVariable Long id,
                                       @RequestBody AssignRequest request) {
        if (request == null || request.memberId == null) {
            throw new IllegalArgumentException("memberId is required to assign item " + id);
        }
        commandService.assignItem(id, request.memberId,
                request.conditionAtAssign != null ? ItemCondition.valueOf(request.conditionAtAssign.trim().toUpperCase()) : null,
                request.notes);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/{id}/return")
    public ResponseEntity<Void> returnItem(@PathVariable Long id,
                                           @RequestBody(required = false) ReturnRequest request) {
        commandService.returnItem(id,
                request != null ? parseCondition(request.conditionAtReturn) : null,
                request != null ? request.notes : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/{id}/retire")
    public ResponseEntity<Void> retire(@PathVariable Long id) {
        commandService.retireItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/items/{id}/dispose")
    public ResponseEntity<Void> dispose(@PathVariable Long id) {
        commandService.disposeItem(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Attribute definitions (for the form / filters) ====================

    @GetMapping("/definitions")
    public ResponseEntity<List<DefView>> listDefinitions(
            @RequestParam(required = false) Long bandId,
            @RequestParam String type,
            @AuthenticationPrincipal OidcUser oidcUser) {
        Band band = resolveBand(bandId, oidcUser);
        ItemType itemType;
        try {
            itemType = ItemType.valueOf(type.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown item type: " + type);
        }
        List<DefView> defs = queryService.getAttributeDefsForType(band != null ? band.getId() : null, itemType).stream()
                .map(d -> {
                    var dv = new DefView();
                    dv.id = d.getId();
                    dv.name = d.getName();
                    dv.dataType = d.getDataType().name();
                    dv.required = d.isRequired();
                    dv.filterable = d.isFilterable();
                    dv.displayInList = d.isDisplayInList();
                    dv.displayOrder = d.getDisplayOrder();
                    dv.options = d.getOptions();
                    return dv;
                })
                .toList();
        return ResponseEntity.ok(defs);
    }

    // ==================== Legacy routes — 410 Gone (F2 S2 acceptance) ====================
    // The pre-F2 model (UniformItem / InstrumentItem / AwardItem / InventoryOrder) is removed.
    // These guards keep old clients failing fast instead of silently hitting the wrong service;
    // use the unified /items endpoints above instead.

    @GetMapping("/orders")
    public ResponseEntity<Void> getOrders() {
        return gone();
    }

    @PostMapping("/orders/uniform")
    public ResponseEntity<Void> placeUniformOrder(@RequestBody(required = false) Object request) {
        return gone();
    }

    @PostMapping("/orders/instrument")
    public ResponseEntity<Void> placeInstrumentOrder(@RequestBody(required = false) Object request) {
        return gone();
    }

    @PostMapping("/orders/{id}/{action:approve|produce|ship|deliver|cancel}")
    public ResponseEntity<Void> legacyOrderAction(@PathVariable Long id, @PathVariable String action) {
        return gone();
    }

    @PostMapping("/orders/{id}/approve")
    public ResponseEntity<Void> approveOrder(@PathVariable Long id) {
        return gone();
    }

    @GetMapping("/uniforms")
    public ResponseEntity<Void> getUniformItems() {
        return gone();
    }

    @GetMapping("/instruments")
    public ResponseEntity<Void> getInstrumentItems() {
        return gone();
    }

    @PostMapping("/uniforms")
    public ResponseEntity<Void> addUniformItem(@RequestBody(required = false) Object request) {
        return gone();
    }

    @PostMapping("/instruments")
    public ResponseEntity<Void> addInstrumentItem(@RequestBody(required = false) Object request) {
        return gone();
    }

    @PostMapping("/uniforms/{id}/assign")
    public ResponseEntity<Void> assignUniform(@PathVariable Long id, @RequestBody(required = false) AssignRequest request) {
        return gone();
    }

    @PostMapping("/instruments/{id}/assign")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id, @RequestBody(required = false) AssignRequest request) {
        return gone();
    }

    @PostMapping("/uniforms/{id}/return")
    public ResponseEntity<Void> returnUniform(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        return gone();
    }

    @PostMapping("/instruments/{id}/return")
    public ResponseEntity<Void> returnInstrument(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        return gone();
    }

    @PostMapping("/uniforms/{id}/retire")
    public ResponseEntity<Void> retireUniform(@PathVariable Long id) {
        return gone();
    }

    @PostMapping("/instruments/{id}/retire")
    public ResponseEntity<Void> retireInstrument(@PathVariable Long id) {
        return gone();
    }

    @PostMapping("/uniforms/{id}/dispose")
    public ResponseEntity<Void> disposeUniform(@PathVariable Long id) {
        return gone();
    }

    @PostMapping("/instruments/{id}/dispose")
    public ResponseEntity<Void> disposeInstrument(@PathVariable Long id) {
        return gone();
    }

    @DeleteMapping("/uniforms/{id}")
    public ResponseEntity<Void> deleteUniformItem(@PathVariable Long id) {
        return gone();
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<Void> deleteInstrumentItem(@PathVariable Long id) {
        return gone();
    }

    @GetMapping("/awards")
    public ResponseEntity<Void> getAwardItems(@RequestParam(required = false) Long teamId) {
        return gone();
    }

    @PostMapping("/awards")
    public ResponseEntity<Void> addAwardItem(@RequestBody(required = false) Object request) {
        return gone();
    }

    @PostMapping("/awards/{id}/assign")
    public ResponseEntity<Void> assignAward(@PathVariable Long id, @RequestBody(required = false) AssignRequest request) {
        return gone();
    }

    @PostMapping("/awards/{id}/return")
    public ResponseEntity<Void> returnAward(@PathVariable Long id) {
        return gone();
    }

    @PostMapping("/awards/{id}/dispose")
    public ResponseEntity<Void> disposeAward(@PathVariable Long id) {
        return gone();
    }

    @PutMapping("/awards/{id}/attributes")
    public ResponseEntity<Void> updateAwardAttributes(@PathVariable Long id, @RequestBody(required = false) Map<String, String> rawAttrs) {
        return gone();
    }


    // ==================== Assignment history (re-anchored under InventoryItem in F2 S3) ====================

    @GetMapping("/uniforms/{id}/history")
    public ResponseEntity<List<?>> getUniformHistory(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.GONE).body(List.of());
    }

    @GetMapping("/instruments/{id}/history")
    public ResponseEntity<List<?>> getInstrumentHistory(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.GONE).body(List.of());
    }

    @GetMapping("/members/{memberId}/assignments")
    public ResponseEntity<?> getMemberAssignments(@PathVariable Long memberId) {
        return ResponseEntity.ok(queryService.getItemsAssignedToMember(memberId).stream()
                .map(this::toItemView).toList());
    }

    @GetMapping("/assignments/active")
    public ResponseEntity<?> getActiveAssignments(@AuthenticationPrincipal OidcUser oidcUser) {
        Band band = resolveActiveBand(oidcUser);
        if (band == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getAllItemsByBand(band.getId()).stream()
                .filter(pl.michalbzowski.windband.domain.inventory.InventoryItem::isAssigned)
                .map(this::toItemView)
                .toList());
    }

    // ==================== Request / response DTOs ====================

    static class ItemView {
        public Long id;
        public String type;
        public String typeDisplayName;
        public String name;
        public String description;
        public String serialNumber;
        public String manufacturer;
        public String model;
        public String condition;
        public String ownershipStatus;
        public String lifecycleStatus;
        public String notes;
        public Long assignedMemberId;
        public String assignedMemberName;
        public Map<Long, String> attributes = new HashMap<>();
    }

    static class DefView {
        public Long id;
        public String name;
        public String dataType;
        public boolean required;
        public boolean filterable;
        public boolean displayInList;
        public int displayOrder;
        public String options;
    }

    public static class CreateItemRequest {
        private Long bandId;
        private String name;
        private String type;
        private String condition;
        private String description;
        private String serialNumber;
        private String manufacturer;
        private String model;
        private Map<String, String> attributes;

        public Long getBandId() { return bandId; }
        public void setBandId(Long bandId) { this.bandId = bandId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Map<String, String> getAttributes() { return attributes; }
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    }

    public static class UpdateItemRequest {
        private String name;
        private String description;
        private String condition;
        private String serialNumber;
        private String manufacturer;
        private String model;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class AssignRequest {
        private Long memberId;
        private String conditionAtAssign;
        private String notes;

        public Long getMemberId() { return memberId; }
        public void setMemberId(Long memberId) { this.memberId = memberId; }
        public String getConditionAtAssign() { return conditionAtAssign; }
        public void setConditionAtAssign(String conditionAtAssign) { this.conditionAtAssign = conditionAtAssign; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class ReturnRequest {
        private String conditionAtReturn;
        private String notes;

        public String getConditionAtReturn() { return conditionAtReturn; }
        public void setConditionAtReturn(String conditionAtReturn) { this.conditionAtReturn = conditionAtReturn; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    private ResponseEntity<Void> gone() {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }
}
