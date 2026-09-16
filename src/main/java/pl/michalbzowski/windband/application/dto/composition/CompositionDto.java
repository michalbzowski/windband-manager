package pl.michalbzowski.windband.application.dto.composition;

import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.time.Instant;

/**
 * Immutable projection of a {@link Composition} row safe to hand to templates and
 * HTTP responses after the owning transaction closes (Shape-C DTO pattern: the
 * service builds this inside its {@code @Transactional(readOnly)} boundary so no
 * lazy association is ever touched downstream).
 */
public record CompositionDto(
        Long id,
        Long bandId,
        String title,
        String description,
        String composer,
        String arranger,
        CompositionStatus status,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Project from a managed entity. Caller must resolve the lazy {@code band}
     * reference before this line executes (it is inside an open transaction for
     * every current call site — see {@code SpringDataCompositionRepository}'s
     * {@code JOIN FETCH} on the list queries, or the explicit
     * {@code Composition.getBand()} dereference for single-row reads).
     */
    public static CompositionDto from(Composition c) {
        return new CompositionDto(
                c.getId(),
                c.getBand() == null ? null : c.getBand().getId(),
                c.getTitle(),
                c.getDescription(),
                c.getComposer(),
                c.getArranger(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
