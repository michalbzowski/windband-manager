package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.util.List;

/**
 * US-4.4 — one resolved row of the "AI proposed voices" preview table.
 *
 * <p>A proposal that matched an instrument on the band's roster carries the instrument name and
 * the member names (in roster order) that would own those pages; a role the AI emitted that
 * matches no member (a stray voice, a new instrument, a misread) is reported with {@code null}
 * members so the human can review it before accepting.
 */
public record AiProposalPreview(
        String role,
        int pageFrom,
        int pageTo,
        double confidence,
        String instrumentNameOrNull,
        List<String> memberNames) {}
