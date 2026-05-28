package pl.michalbzowski.windband.application.dto;

public record InstrumentAttributeDefDto(
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
