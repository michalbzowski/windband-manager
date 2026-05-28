package pl.michalbzowski.windband.application.dto;

public record MemberAttributeDefDto(
        Long id,
        String name,
        String type,
        boolean required,
        boolean displayInList,
        int displayOrder,
        String options
) {}