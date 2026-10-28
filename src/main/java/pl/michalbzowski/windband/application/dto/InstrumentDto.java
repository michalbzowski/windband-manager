package pl.michalbzowski.windband.application.dto;

import pl.michalbzowski.windband.domain.member.Instrument;

/**
 * Flat HTTP representation of a band-scoped instrument (the UI calls it a "tag").
 *
 * <p>The REST layer must never serialise the {@link Instrument} entity itself: the entity carries
 * LAZY {@code band} and {@code aliasOf} associations and the app runs with
 * {@code spring.jpa.open-in-view: false}, so Jackson would hit
 * {@code LazyInitializationException: could not initialize proxy [...Band#N] - no Session}
 * while writing the response of any tag/instrument mutation (user-reported on tag re-ordering,
 * Oct 2026 — see {@code TagEditLazyBandRegressionTest}). This DTO is built inside the request
 * scope from scalar getters only, so it needs no session and leaks no object graph across the API.</p>
 *
 * @param id           database identifier of the tag
 * @param name         display name (unique per band)
 * @param description  optional free-text description
 * @param sortPriority list order shown in the tags UI (0 when unset)
 */
public record InstrumentDto(Long id, String name, String description, Integer sortPriority) {

    /**
     * Maps an entity to its wire form. Reads scalar properties only — never touches the
     * LAZY {@code band}/{@code aliasOf} proxies.
     */
    public static InstrumentDto from(Instrument instrument) {
        return new InstrumentDto(
                instrument.getId(),
                instrument.getName(),
                instrument.getDescription(),
                instrument.getSortPriority());
    }
}
