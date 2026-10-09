package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.inventory.UnifiedInventoryItemCommandService;
import pl.michalbzowski.windband.application.query.inventory.UnifiedInventoryItemQueryService;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.inventory.ItemCondition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final UnifiedInventoryItemCommandService commandService;
    private final UnifiedInventoryItemQueryService queryService;
    private final BandQueryService bandQueryService;

    private Band resolveActiveBand(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            Long teamId = wu.getActiveTeamId();
            if (teamId != null) {
                return bandQueryService.getBandById(teamId);
            }
        }
        return null;
    }

    private Optional<Long> requireMember(Long memberId, Long itemId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required to bind item " + itemId);
        }
        return Optional.of(memberId);
    }

    private ItemCondition parseCondition(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ItemCondition.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // === Orders (legacy model removed in F2 S1 — 410 until unified endpoints in S2) ===

    @GetMapping("/orders")
    public ResponseEntity<List<?>> getOrders() {
        return ResponseEntity.status(HttpStatus.GONE).body(List.of());
    }

    @PostMapping("/orders/uniform")
    public ResponseEntity<Void> placeUniformOrder(@RequestBody PlaceOrderRequest request) {
        return orderGone();
    }

    @PostMapping("/orders/instrument")
    public ResponseEntity<Void> placeInstrumentOrder(@RequestBody PlaceOrderRequest request) {
        return orderGone();
    }

    @PostMapping("/orders/{id}/approve")
    public ResponseEntity<Void> approveOrder(@PathVariable Long id) {
        return orderGone();
    }

    @PostMapping("/orders/{id}/produce")
    public ResponseEntity<Void> produceOrder(@PathVariable Long id) {
        return orderGone();
    }

    @PostMapping("/orders/{id}/ship")
    public ResponseEntity<Void> shipOrder(@PathVariable Long id) {
        return orderGone();
    }

    @PostMapping("/orders/{id}/deliver")
    public ResponseEntity<Void> deliverOrder(@PathVariable Long id) {
        return orderGone();
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        return orderGone();
    }

    private ResponseEntity<Void> orderGone() {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    // === Items ===

    @GetMapping("/uniforms")
    public ResponseEntity<?> getUniformItems() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstrumentItems() {
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/uniforms")
    public ResponseEntity<?> addUniformItem(@AuthenticationPrincipal OidcUser oidcUser,
                                             @RequestBody AddItemRequest request) {
        // Legacy creation flow removed in F2 S1 — replaced by unified item API (S2).
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @PostMapping("/instruments")
    public ResponseEntity<?> addInstrumentItem(@AuthenticationPrincipal OidcUser oidcUser,
                                                @RequestBody AddInstrumentRequest request) {
        // Legacy creation flow removed in F2 S1 — replaced by unified item API (S2).
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    // === Assign / Return / Retire / Dispose (now via unified InventoryItem) ===

    @PostMapping("/uniforms/{id}/assign")
    public ResponseEntity<Void> assignUniform(@PathVariable Long id, @RequestBody AssignRequest request) {
        requireMember(request.getMemberId(), id).ifPresent(m ->
                commandService.assignItem(id, m, parseCondition(request.getConditionAtAssign()), null));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/assign")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id, @RequestBody AssignRequest request) {
        requireMember(request.getMemberId(), id).ifPresent(m ->
                commandService.assignItem(id, m, parseCondition(request.getConditionAtAssign()), null));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/return")
    public ResponseEntity<Void> returnUniform(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnItem(id,
                request != null ? parseCondition(request.getConditionAtReturn()) : null,
                request != null ? request.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/return")
    public ResponseEntity<Void> returnInstrument(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnItem(id,
                request != null ? parseCondition(request.getConditionAtReturn()) : null,
                request != null ? request.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/retire")
    public ResponseEntity<Void> retireUniform(@PathVariable Long id) {
        commandService.retireItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/retire")
    public ResponseEntity<Void> retireInstrument(@PathVariable Long id) {
        commandService.retireItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/dispose")
    public ResponseEntity<Void> disposeUniform(@PathVariable Long id) {
        commandService.disposeItem(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/dispose")
    public ResponseEntity<Void> disposeInstrument(@PathVariable Long id) {
        commandService.disposeItem(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/uniforms/{id}")
    public ResponseEntity<Void> deleteUniformItem(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<Void> deleteInstrumentItem(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    // === Awards (legacy AwardItem model removed in F2 S1 — 410 until unified endpoints in S2) ===

    @GetMapping("/awards")
    public ResponseEntity<?> getAwardItems(@RequestParam(required = false) Long teamId) {
        if (teamId == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(queryService.getItemsByBandAndType(teamId, pl.michalbzowski.windband.domain.inventory.ItemType.AWARD));
    }

    @PostMapping("/awards")
    public ResponseEntity<?> addAwardItem(@RequestBody AddAwardRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(null);
    }

    @PostMapping("/awards/{id}/assign")
    public ResponseEntity<Void> assignAward(@PathVariable Long id, @RequestBody AssignRequest request) {
        requireMember(request.getMemberId(), id).ifPresent(m -> commandService.assignItem(id, m, null, null));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/awards/{id}/return")
    public ResponseEntity<Void> returnAward(@PathVariable Long id) {
        commandService.returnItem(id, null, null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/awards/{id}/dispose")
    public ResponseEntity<Void> disposeAward(@PathVariable Long id) {
        commandService.disposeItem(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/awards/{id}/attributes")
    public ResponseEntity<Void> updateAwardAttributes(@PathVariable Long id, @RequestBody Map<String, String> attributes) {
        java.util.Map<Long, String> typed = new java.util.HashMap<>();
        if (attributes != null) {
            attributes.forEach((k, v) -> {
                try {
                    typed.put(Long.parseLong(k), v);
                } catch (NumberFormatException ignored) {
                    // skip keys that are not attribute def ids
                }
            });
        }
        commandService.updateItemAttributes(id, typed, null);
        return ResponseEntity.ok().build();
    }

    // === Assignment history (re-anchored under InventoryItem in F2 S3) ===

    @GetMapping("/uniforms/{id}/history")
    public ResponseEntity<?> getUniformHistory(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/instruments/{id}/history")
    public ResponseEntity<?> getInstrumentHistory(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/members/{memberId}/assignments")
    public ResponseEntity<?> getMemberAssignments(@PathVariable Long memberId, @AuthenticationPrincipal OidcUser oidcUser) {
        return ResponseEntity.ok(queryService.getItemsAssignedToMember(memberId));
    }

    @GetMapping("/assignments/active")
    public ResponseEntity<?> getActiveAssignments(@AuthenticationPrincipal OidcUser oidcUser) {
        Band band = resolveActiveBand(oidcUser);
        if (band == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getAllItemsByBand(band.getId()).stream()
                .filter(pl.michalbzowski.windband.domain.inventory.InventoryItem::isAssigned)
                .toList());
    }

    // === Request DTOs ===

    @Data
    public static class PlaceOrderRequest {
        private Long memberId;
        private Map<String, String> attributes;
    }

    @Data
    public static class AddItemRequest {
        private Long memberId;
        private Map<String, String> attributes;
    }

    @Data
    public static class AddInstrumentRequest {
        private Long memberId;
        private Map<String, String> attributes;
    }

    @Data
    public static class AssignRequest {
        private Long memberId;
        private String conditionAtAssign;
    }

    @Data
    public static class ReturnRequest {
        private String conditionAtReturn;
        private String notes;
    }

    @Data
    public static class AddAwardRequest {
        private Long teamId;
        private String description;
        private Long memberId;
        private Map<String, String> attributes;
    }
}
