package pl.michalbzowski.windband.domain.composition;

/**
 * How a {@link CompositionInstrument} part mapping was produced — drives downstream
 * UX (manual vs AI preview) and the US-4.x gate ("AI proposals are never used unverified").
 *
 * <ul>
 *   <li>{@link #MANUAL} — set by the librarian; no AI confidence applies.</li>
 *   <li>{@link #AI}     — produced by an AI analysis strategy (US-4.x); carries a
 *       {@code confidenceScore} and always requires human verification before parts
 *       can be distributed.</li>
 *   <li>{@link #HYBRID} — partially manual, partially AI-derived (the librarian
 *       tweaked the AI suggestion but kept the AI-proposed shape).</li>
 * </ul>
 *
 * <p>The SQL CHECK constraint on {@code composition_instruments.source} stores these
 * by name ({@code 'AI'}, {@code 'MANUAL'}, {@code 'HYBRID'}) — see V36.
 */
public enum PartSource {
    AI,
    MANUAL,
    HYBRID;

    /** True for any source where the librarian must explicitly verify before distribution (US-4.x gate). */
    public boolean requiresHumanVerification() {
        return this == AI || this == HYBRID;
    }
}
