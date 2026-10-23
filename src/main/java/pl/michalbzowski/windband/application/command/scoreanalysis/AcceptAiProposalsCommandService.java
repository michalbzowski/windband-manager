package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;
import pl.michalbzowski.windband.application.query.scoreanalysis.RoleInstrumentResolver;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

/**
 * Faza D / US-4.5 — acceptance of AI-proposed voices: creates {@link CompositionInstrument}
 * rows for the accepted proposal rows, bound to the analysed score file (V44), stamped with
 * the proposing confidence and — because acceptance IS the human review — immediately verified
 * by the calling user (US-4.5 "AI never unverified"; opens US-3.03 readiness).
 *
 * <p>Guards, in order: proposal row must exist in this analysis's {@code arrangement.json} with
 * an identical page range (the endpoint only accepts what the AI actually suggested — anything
 * else belongs to {@code addPart}); the instrument must belong to the band; a role that already
 * has a part in the composition is SKIPPED (never overwrites a human mapping) and reported.
 *
 * <p>{@code PartSource}: the chosen instrument equals the resolver's candidate for the role →
 * {@link PartSource#AI} (accepted verbatim); any other choice → {@link PartSource#HYBRID}.
 */
@Service
@RequiredArgsConstructor
public class AcceptAiProposalsCommandService {

    private final CompositionRepository compositionRepository;
    private final ScoreAnalysisRepository analysisRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final InstrumentRepository instrumentRepository;
    private final CompositionInstrumentRepository partRepository;
    private final RoleInstrumentResolver resolver;

    /** One accepted proposal row, as submitted by the UI (role/range copied from the preview). */
    public record AcceptedRow(String role, int pageFrom, int pageTo, Long instrumentId, Double confidence) {}

    public record SkippedRow(String role, String reason) {}

    public record AcceptResult(int created, List<SkippedRow> skipped) {}

    /**
     * Accepts the listed proposal rows onto the composition.
     *
     * @param verifier human identity stamped into {@code verifiedBy} (e.g. user id) — required
     * @throws IllegalArgumentException unknown role/range/instrument for this analysis (→ 400)
     * @throws IllegalStateException cross-band, missing artefact or unfinished analysis (→ 409)
     */
    @Transactional
    public AcceptResult accept(Long compositionId, Long bandId, Long analysisId,
                               List<AcceptedRow> rows, String verifier) {
        if (compositionId == null || bandId == null || analysisId == null) {
            throw new IllegalArgumentException("compositionId, bandId i analysisId są wymagane.");
        }
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Lista akceptowanych wierszy jest pusta.");
        }
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("Akceptacja wymaga tożsamości użytkownika (US-4.5).");
        }

        Composition composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + compositionId + " nie należy do zespołu " + bandId + "."));
        ScoreAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("Analiza " + analysisId + " nie znaleziona."));
        if (!composition.getId().equals(analysis.getComposition().getId())) {
            throw new IllegalStateException("Analiza " + analysisId + " nie należy do utworu " + compositionId + ".");
        }
        if (analysis.getPhase() != ScoreAnalysis.Phase.SUCCEEDED) {
            throw new IllegalStateException("Analiza nie została ukończona — brak propozycji do akceptacji.");
        }
        Path artifactPath = Path.of(analysis.getArrangementJsonPath());
        List<AIProposals> proposals;
        try {
            proposals = JsonAiProposals.parse(Files.readString(artifactPath, StandardCharsets.UTF_8));
        } catch (RuntimeException | java.io.IOException e) {
            throw new IllegalStateException(
                    "Artefakt arrangement.json analizy " + analysisId + " nie jest odczytelny.", e);
        }

        ScoreFile scoreFile = scoreFileRepository.findById(analysis.getScoreFileId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Plik nut analizowany w biegu " + analysisId + " nie istnieje."));

        java.util.Map<String, AIProposals> known = new java.util.HashMap<>();
        for (AIProposals p : proposals) {
            known.putIfAbsent(normalize(p.role()), p);
        }

        Set<String> existingRoles = new HashSet<>();
        for (CompositionInstrument part : partRepository.findAllByComposition(composition)) {
            existingRoles.add(normalize(part.getInstrumentRole()));
        }

        List<SkippedRow> skipped = new ArrayList<>();
        int created = 0;
        for (AcceptedRow row : rows) {
            String normRole = normalize(row.role());
            if (normRole.isEmpty()) {
                throw new IllegalArgumentException("Wiersz bez roli nie może być zaakceptowany.");
            }
            AIProposals source = known.get(normRole);
            if (source == null) {
                throw new IllegalArgumentException(
                        "Rola \"" + row.role() + "\" nie występuje w propozycjach analizy " + analysisId + ".");
            }
            if (source.pageFrom() != row.pageFrom() || source.pageTo() != row.pageTo()) {
                throw new IllegalArgumentException(
                        "Zakres stron wiersza \"" + row.role() + "\" nie zgadza się z propozycjami analizy AI.");
            }
            if (existingRoles.contains(normRole)) {
                skipped.add(new SkippedRow(row.role(), "partia o tej roli już istnieje w utworze"));
                continue;
            }

            Instrument instrument = instrumentRepository.findByIdAndBandId(row.instrumentId(), bandId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Instrument " + row.instrumentId() + " nie należy do zespołu " + bandId + "."));

            Optional<Instrument> resolved = resolver.resolveInstrument(bandId, row.role());
            PartSource sourceValue = resolved.isPresent()
                    ? (resolved.get().getId().equals(instrument.getId())
                        ? PartSource.AI : PartSource.HYBRID)
                    : PartSource.HYBRID;

            double confidence = row.confidence() == null
                    ? 1.0d
                    : Math.max(0.0d, Math.min(1.0d, row.confidence()));

            CompositionInstrument part = CompositionInstrument.forComposition(
                    composition, instrument, row.role(), row.pageFrom(), row.pageTo(),
                    null, scoreFile, sourceValue, confidence);
            part.verify(verifier, Instant.now());
            partRepository.save(part);
            existingRoles.add(normRole);
            created++;
        }
        return new AcceptResult(created, skipped);
    }

    private static String normalize(String s) {
        return s == null ? "" : RoleInstrumentResolver.normalize(s);
    }
}
