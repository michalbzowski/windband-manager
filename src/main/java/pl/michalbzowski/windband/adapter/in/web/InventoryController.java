package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    // === Orders ===

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders() {
        return ResponseEntity.ok(queryService.getAllOrders());
    }

    @PostMapping("/orders/uniform")
    public ResponseEntity<?> placeUniformOrder(@RequestBody PlaceOrderRequest request) {
        var order = commandService.placeUniformOrder(
                request.getMemberId(), request.getItemName(), request.getDescription(), request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @PostMapping("/orders/instrument")
    public ResponseEntity<?> placeInstrumentOrder(@RequestBody PlaceOrderRequest request) {
        var order = commandService.placeInstrumentOrder(
                request.getMemberId(), request.getItemName(), request.getDescription(), request.getAttributes());
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
        var item = commandService.addUniformItem(
                request.getName(), request.getDescription(),
                request.getMemberId(), OwnershipStatus.valueOf(request.getOwnershipStatus()),
                request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/instruments")
    public ResponseEntity<?> addInstrumentItem(@RequestBody AddInstrumentRequest request) {
        var item = commandService.addInstrumentItem(
                request.getName(), request.getBrand(), request.getSerialNumber(),
                request.getDescription(), request.getMemberId(),
                OwnershipStatus.valueOf(request.getOwnershipStatus()),
                request.getAttributes());
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    // === Assign / Return / Retire / Dispose ===

    @PostMapping("/uniforms/{id}/assign")
    public ResponseEntity<Void> assignUniform(@PathVariable Long id, @RequestBody AssignRequest request) {
        commandService.assignUniformToMember(id, request.getMemberId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/assign")
    public ResponseEntity<Void> assignInstrument(@PathVariable Long id, @RequestBody AssignRequest request) {
        commandService.assignInstrumentToMember(id, request.getMemberId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/uniforms/{id}/return")
    public ResponseEntity<Void> returnUniform(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnUniform(id, request != null ? request.getNotes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/instruments/{id}/return")
    public ResponseEntity<Void> returnInstrument(@PathVariable Long id, @RequestBody(required = false) ReturnRequest request) {
        commandService.returnInstrument(id, request != null ? request.getNotes() : null);
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

    // === Assignment history ===

    @GetMapping("/uniforms/{id}/history")
    public ResponseEntity<?> getUniformHistory(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getHistoryByUniformItem(id));
    }

    @GetMapping("/instruments/{id}/history")
    public ResponseEntity<?> getInstrumentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(queryService.getHistoryByInstrumentItem(id));
    }

    @GetMapping("/members/{memberId}/assignments")
    public ResponseEntity<?> getMemberAssignments(@PathVariable Long memberId) {
        return ResponseEntity.ok(queryService.getHistoryByMember(memberId));
    }

    @GetMapping("/assignments/active")
    public ResponseEntity<?> getActiveAssignments() {
        return ResponseEntity.ok(queryService.getActiveAssignments());
    }

    // === Request DTOs ===

    @Data
    public static class PlaceOrderRequest {
        private Long memberId;
        private String itemName;
        private String description;
        private Map<String, String> attributes;
    }

    @Data
    public static class AddItemRequest {
        private String name;
        private String description;
        private Long memberId;
        private String ownershipStatus;
        private Map<String, String> attributes;
    }

    @Data
    public static class AddInstrumentRequest {
        private String name;
        private String brand;
        private String serialNumber;
        private String description;
        private Long memberId;
        private String ownershipStatus;
        private Map<String, String> attributes;
    }

    @Data
    public static class AssignRequest {
        private Long memberId;
    }

    @Data
    public static class ReturnRequest {
        private String notes;
    }
}
