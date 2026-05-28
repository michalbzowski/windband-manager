package pl.michalbzowski.windband.application.dto;

import java.time.LocalDateTime;

/**
 * Flat DTO for inventory orders.
 */
public record InventoryOrderDto(
        Long id,
        String requesterName,
        String orderType,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String notes,
        String orderNumber
) {}
