package pl.michalbzowski.windband.application.dto.composition;

import java.time.Instant;

/**
 * Immutable projection of ONE {@code composition_instruments} row as shown on the
 * US-3.03 Parts tab: every field a template could need, resolved by the service
 * while the owning transaction is still open — including {@code instrumentName},
 * which touches the lazy {@code instrument} association a template must never
 * dereference after the session closes ({@code LazyInitializationException}).
 */
public record CompositionInstrumentDto(
        Long id,
        Long compositionId,
        String instrumentRole,
        String instrumentName,
        Integer pageFrom,
        Integer pageTo,
        String fileRef,
        /** US-7.14 — the explicit ScoreFile this mapping points at (nullable for legacy rows). */
        Long scoreFileId,
        /** Optional display name of that ScoreFile (resolved eagerly during the projection). */
        String scoreFileName,
        pl.michalbzowski.windband.domain.composition.PartSource source,
        Double confidenceScore,
        String verifiedBy,
        Instant verifiedAt) {

    /**
     * US- detail-page cleanup — mockup shows the page range as ONE column ("Strony 1–22")
     * instead of two bare integers. Kept on the DTO (not in the template) so the rendering rule
     * is unit-testable and both bound/legacy rows share it; a single page renders "7", not "7–7".
     */
    public String pageRange() {
        if (pageFrom == null || pageTo == null) {
            return "—";
        }
        if (pageFrom.equals(pageTo)) {
            return String.valueOf(pageFrom);
        }
        return pageFrom + "–" + pageTo;
    }
}
