package pl.michalbzowski.windband.application.dto;

/**
 * Flat DTO for inventory items (uniform or instrument) for list views.
 */
public record InventoryItemDto(
        Long id,
        String name,
        String type,           // "UNIFORM" or "INSTRUMENT"
        String brand,
        String serialNumber,
        String description,
        String assignedMemberName,
        String ownershipStatus,
        String lifecycleStatus
) {}
