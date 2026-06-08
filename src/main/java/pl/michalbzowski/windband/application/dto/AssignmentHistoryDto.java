package pl.michalbzowski.windband.application.dto;

import java.time.LocalDate;

/**
 * Flat DTO for assignment history entries — safe for Thymeleaf (no lazy loading).
 * Includes full audit trail: who assigned, condition at assignment and return.
 */
public record AssignmentHistoryDto(
        Long id,
        String itemName,
        String itemType,       // "UNIFORM" or "INSTRUMENT"
        Long itemId,
        String memberName,
        String assignedByName,
        LocalDate assignedAt,
        LocalDate returnedAt,
        boolean active,
        String conditionAtAssign,
        String conditionAtReturn,
        String notes
) {}
