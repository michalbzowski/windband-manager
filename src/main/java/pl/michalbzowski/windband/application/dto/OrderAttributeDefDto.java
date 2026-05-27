package pl.michalbzowski.windband.application.dto;

public record OrderAttributeDefDto(
        Long id,
        String name,
        String type,
        boolean required,
        int displayOrder,
        String options
) {}
