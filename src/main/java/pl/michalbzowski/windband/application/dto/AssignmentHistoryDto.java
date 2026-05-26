package pl.michalbzowski.windband.application.dto;

import java.time.LocalDate;

/**
 * Flat DTO for assignment history entries — safe for Thymeleaf (no lazy loading).
 */
public record AssignmentHistoryDto(
        Long id,
        String itemName,
        String itemType,       // "UNIFORM" or "INSTRUMENT"
        Long itemId,
        String memberName,
        LocalDate assignedAt,
        LocalDate returnedAt,
        boolean active,
        String notes
) {}
