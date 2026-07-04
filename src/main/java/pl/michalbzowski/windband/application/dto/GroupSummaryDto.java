package pl.michalbzowski.windband.application.dto;

public record GroupSummaryDto(
        Long id,
        String name,
        String description,
        int memberCount,
        boolean dynamic
) {}
