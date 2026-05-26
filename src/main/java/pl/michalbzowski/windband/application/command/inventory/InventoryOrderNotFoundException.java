package pl.michalbzowski.windband.application.command.inventory;

public class InventoryOrderNotFoundException extends RuntimeException {
    public InventoryOrderNotFoundException(Long id) {
        super("Inventory order not found: " + id);
    }
}
