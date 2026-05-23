package pl.michalbzowski.windband.adapter.in.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.michalbzowski.windband.application.command.inventory.InventoryCommandService;
import pl.michalbzowski.windband.domain.inventory.InventoryRepository;
import pl.michalbzowski.windband.domain.inventory.OwnershipStatus;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryCommandService commandService;
    private final InventoryRepository inventoryRepository;

    @GetMapping("/uniforms")
    public ResponseEntity<?> getUniformItems() {
        return ResponseEntity.ok(inventoryRepository.findAllUniformItems());
    }

    @GetMapping("/instruments")
    public ResponseEntity<?> getInstrumentItems() {
        return ResponseEntity.ok(inventoryRepository.findAllInstrumentItems());
    }

    @PostMapping("/uniforms")
    public ResponseEntity<?> addUniformItem(@RequestBody AddInventoryItemRequest request) {
        var item = commandService.addUniformItem(
                request.getName(), request.getDescription(),
                request.getMemberId(), OwnershipStatus.valueOf(request.getOwnershipStatus()));
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @PostMapping("/instruments")
    public ResponseEntity<?> addInstrumentItem(@RequestBody AddInventoryItemRequest request) {
        var item = commandService.addInstrumentItem(
                request.getName(), request.getBrand(), request.getSerialNumber(),
                request.getDescription(), request.getMemberId(),
                OwnershipStatus.valueOf(request.getOwnershipStatus()));
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
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

    @Data
    public static class AddInventoryItemRequest {
        private String name;
        private String description;
        private String brand;
        private String serialNumber;
        private Long memberId;
        private String ownershipStatus;
    }
}
