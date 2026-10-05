package pl.michalbzowski.windband.application.dto.scoreanalysis;

import pl.michalbzowski.windband.domain.composition.PartSource;

/**
 * US-4.4 — one row of the AI's part→page proposal for a composition. The shape mirrors
 * {@link CompositionInstrument}: a role label, a 1-based [pageFrom, pageTo] range, an
 * AI confidence in [0.0, 1.0], and the source that the user selected for this row when
 * accepting (the AI's own suggestion is {@link PartSource#AI}; a librarian tweak becomes
 * {@link PartSource#HYBRID}).
 *
 * <p>Immutability: value records, so the preview table can render edits without any mutator.
 */
public record AIProposals(String role, int pageFrom, int pageTo, double confidence, PartSource source) {

    public AIProposals {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        if (pageFrom < 1 || pageTo < 1) {
            throw new IllegalArgumentException("page range must be >= 1 (got pageFrom=" + pageFrom + ", pageTo=" + pageTo + ")");
        }
        if (pageFrom > pageTo) {
            throw new IllegalArgumentException("pageFrom (" + pageFrom + ") must not exceed pageTo (" + pageTo + ")");
        }
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0] (got " + confidence + ")");
        }
    }
}
