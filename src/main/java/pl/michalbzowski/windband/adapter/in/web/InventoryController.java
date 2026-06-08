package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.adapter.in.security.WindbandOidcUser;
import pl.michalbzowski.windband.application.command.inventory.InventoryCommandService;
import pl.michalbzowski.windband.application.query.inventory.InventoryQueryService;
import pl.michalbzowski.windband.domain.inventory.OrderStatus;
import pl.michalbzowski.windband.domain.inventory.OwnershipStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryCommandService commandService;
    private final InventoryQueryService queryService;

    private Long resolveActiveTeamId(OidcUser oidcUser) {
        if (oidcUser instanceof WindbandOidcUser wu) {
            return wu.getActiveTeamId();
        }
        return null;
    }

    // === Orders ===

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders() {
        return ResponseEntity.ok(queryService.getAllOrders());
    }

    @PostMapping("/orders/uniform")
    public ResponseEntity<?> placeUniformOrder(@RequestBody PlaceOrderRequest request) {
        var order = commandService.placeUniformOrder(
                request.getMemberId(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/orders/instrument")
    public ResponseEntity<?> placeInstrumentOrder(@RequestBody PlaceOrderRequest request) {
        var order = commandService.placeInstrumentOrder(
                request.getMemberId(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/orders/{id}/approve")
    public ResponseEntity<Void> approveOrder(@PathVariable Long id) {
        commandService.advanceOrderToApproval(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{id}/produce")
    public ResponseEntity<Void> produceOrder(@PathVariable Long id) {
        commandService.advanceOrderToProduction(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{id}/ship")
    public ResponseEntity<Void> shipOrder(@PathVariable Long id) {
        commandService.markOrderShipped(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{id}/deliver")
    public ResponseEntity<?> deliverOrder(@PathVariable Long id) {
        commandService.markOrderDelivered(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        commandService.cancelOrder(id);
        return ResponseEntity.ok().build();
    }

    // === Items ===

    @GetMapping("/uniforms")
    public ResponseEntity<?> getUniformItems() {
        return ResponseEntity.ok(queryService.getAllUniformItems());
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstrumentItems() {
        return ResponseEntity.ok(queryService.getAllInstrumentItems());
    }

    @PostMapping("/uniforms")
    public ResponseEntity<?> addUniformItem(@RequestBody AddItemRequest request) {
        var item = commandService.addUniformItem(request.getMemberId(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/instruments")
    public ResponseEntity<?> addInstrumentItem(@RequestBody AddInstrumentRequest request) {
        var item = commandService.addInstrumentItem(request.getMemberId(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    // === Assign / Return / Retire / Dispose ===

    @PostMapping("/uniforms/{id}/assign")
    public ResponseEntity<Void> assignUniform(@PathVariable Long id, @RequestBody AssignRequest request) {
        commandService.assignUniformToMember(id, request.getMemberId(), request.getConditionAtAssign());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/assign")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id, @RequestBody AssignRequest request) {
        commandService.assignInstrumentToMember(id, request.getMemberId(), request.getConditionAtAssign());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/return")
    public ResponseEntity<Void> returnUniform(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnUniform(id,
                request != null ? request.getConditionAtReturn() : null,
                request != null ? request.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/return")
    public ResponseEntity<Void> returnInstrument(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnInstrument(id,
                request != null ? request.getConditionAtReturn() : null,
                request != null ? request.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/retire")
    public ResponseEntity<Void> retireUniform(@PathVariable Long id) {
        commandService.retireUniform(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/retire")
    public ResponseEntity<Void> retireInstrument(@PathVariable Long id) {
        commandService.retireInstrument(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/dispose")
    public ResponseEntity<Void> disposeUniform(@PathVariable Long id) {
        commandService.disposeUniform(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/dispose")
    public ResponseEntity<Void> disposeInstrument(@PathVariable Long id) {
        commandService.disposeInstrument(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/uniforms/{id}")
    public ResponseEntity<Void> deleteUniformItem(@PathVariable Long id) {
        commandService.deleteUniformItem(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/instruments/{id}")
    public ResponseEntity<Void> deleteInstrumentItem(@PathVariable Long id) {
        commandService.deleteInstrumentItem(id);
        return ResponseEntity.noContent().build();
    }

    // === Awards ===

    @GetMapping("/awards")
    public ResponseEntity<?> getAwardItems(@RequestParam(required = false) Long teamId) {
        if (teamId == null) {
            return ResponseEntity.ok(queryService.getAllAwardItems(null));
        }
        return ResponseEntity.ok(queryService.getAllAwardItems(teamId));
    }

    @PostMapping("/awards")
    public ResponseEntity<?> addAwardItem(@RequestBody AddAwardRequest request) {
        var item = commandService.addAwardItem(
                request.getTeamId(), request.getName(), request.getDescription(),
                request.getMemberId(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/awards/{id}/assign")
    public ResponseEntity<Void> assignAward(@PathVariable Long id, @RequestBody AssignRequest request) {
        commandService.assignAwardToMember(id, request.getMemberId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/awards/{id}/return")
    public ResponseEntity<Void> returnAward(@PathVariable Long id) {
        commandService.returnAward(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/awards/{id}/dispose")
    public ResponseEntity<Void> disposeAward(@PathVariable Long id) {
        commandService.disposeAward(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/awards/{id}/attributes")
    public ResponseEntity<Void> updateAwardAttributes(@PathVariable Long id, @RequestBody Map<String, String> attributes) {
        commandService.updateAwardAttributes(id, attributes);
        return ResponseEntity.ok().build();
    }

    // === Assignment history ===

    @GetMapping("/uniforms/{id}/history")
    public ResponseEntity<?> getUniformHistory(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        Long teamId = resolveActiveTeamId(oidcUser);
        if (teamId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getHistoryByUniformItem(id, teamId));
    }

    @GetMapping("/instruments/{id}/history")
    public ResponseEntity<?> getInstrumentHistory(@PathVariable Long id, @AuthenticationPrincipal OidcUser oidcUser) {
        Long teamId = resolveActiveTeamId(oidcUser);
        if (teamId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getHistoryByInstrumentItem(id, teamId));
    }

    @GetMapping("/members/{memberId}/assignments")
    public ResponseEntity<?> getMemberAssignments(@PathVariable Long memberId, @AuthenticationPrincipal OidcUser oidcUser) {
        Long teamId = resolveActiveTeamId(oidcUser);
        if (teamId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getHistoryByMember(memberId, teamId));
    }

    @GetMapping("/assignments/active")
    public ResponseEntity<?> getActiveAssignments(@AuthenticationPrincipal OidcUser oidcUser) {
        Long teamId = resolveActiveTeamId(oidcUser);
        if (teamId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(queryService.getActiveAssignments(teamId));
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
        private String name;
        private String description;
        private Long memberId;
        private Map<String, String> attributes;
    }
}
