package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.List;

/**
 * US-4.4 / Faza D — one resolved row of the "AI proposed voices" preview table, as rendered by
 * {@link ScoreAnalysisProposalsQueryService}.
 *
 * <p>A proposal that matched an instrument on the band's roster carries {@code instrumentIdOrNull}
 * + name and the member names that would own those pages; a role the AI emitted that matches no
 * band instrument (a stray voice, a new instrument, a misread) is reported with {@code null}s so
 * the human can still pick any band instrument for it at accept time.
 */
public record AiProposalPreview(
        String role,
        int pageFrom,
        int pageTo,
        double confidence,
        Long instrumentIdOrNull,
        String instrumentNameOrNull,
        List<String> memberNames) {}
