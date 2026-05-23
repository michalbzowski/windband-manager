package pl.michalbzowski.windband.application.command.inventory;

public class InventoryItemNotFoundException extends RuntimeException {
    public InventoryItemNotFoundException(Long id) {
        super("Inventory item not found: " + id);
    }
}
