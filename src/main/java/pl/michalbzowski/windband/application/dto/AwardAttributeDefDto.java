package pl.michalbzowski.windband.application.dto;

public record AwardAttributeDefDto(
        Long id,
        String name,
        String type,
        boolean required,
        boolean displayInList,
        int displayOrder,
        String options,
        Long dependsOnAttributeId,
        String dependsOnValue
) {}
