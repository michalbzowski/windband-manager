package pl.michalbzowski.windband.application.dto.composition;

import java.util.List;

/**
 * The full US-3.03 read target: composition metadata + every part-row of that
 * composition, pre-resolved inside one {@code @Transactional(readOnly = true)}
 * service call so Thymeleaf (or an HTTP response) never touches a detached lazy
 * proxy. See {@code CompositionQueryService#getCompositionWithParts}.
 */
public record CompositionWithPartsDto(
        CompositionDto composition,
        List<CompositionInstrumentDto> parts) {

    public CompositionWithPartsDto {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
