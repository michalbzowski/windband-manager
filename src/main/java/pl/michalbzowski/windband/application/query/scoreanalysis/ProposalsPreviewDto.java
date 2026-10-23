package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.List;

/**
 * Faza D — the full payload of one "AI proposals" preview request.
 *
 * <ul>
 *   <li>{@code work} — title/composer/arranger the analyzer read from page 1 (may be nulls);</li>
 *   <li>{@code instruments} — the band's instruments in sort order (the accept form's select);</li>
 *   <li>{@code proposals} — one {@link AiProposalPreview} per row of {@code arrangement.json}.
 * </ul>
 */
public record ProposalsPreviewDto(
        WorkMeta work,
        List<InstrumentOption> instruments,
        List<AiProposalPreview> proposals) {

    /** Work metadata the analyzer extracted ("work" node of analyzer-raw.json). */
    public record WorkMeta(String title, String composer, String arranger) {
        public static final WorkMeta EMPTY = new WorkMeta(null, null, null);
    }

    /** One selectable band instrument for the accept form. */
    public record InstrumentOption(Long id, String name) {}
}
