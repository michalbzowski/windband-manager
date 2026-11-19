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
 * else belongs to {@code addPart}); the instrument must belong to the band; an EXACT duplicate
 * (same role AND same page range) part already in the composition is SKIPPED (never overwrites a
 * human mapping) and reported. A repeated role with a DIFFERENT page range is a distinct voice
 * block (e.g. „Puzon I B♭" on pages 45 and 48 of Amerland) — both blocks are accepted verbatim.
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
     * One accepted proposal row WITH a page-range correction by the user (flagged feature:
     * „raport spójności + akceptacja z korektą").
     *
     * <p>{@code role} + {@code aiPageFrom/aiPageTo} identify the AI proposal row (the anchor for
     * accountability and the artifact's confidence stamp); {@code pageFrom/pageTo} is the FINAL
     * range — identical to the AI's when the user edited nothing, different when corrected.
     */
    public record AcceptedRowCorrected(
            String role, int aiPageFrom, int aiPageTo, int pageFrom, int pageTo, Long instrumentId) {}

    /** Result of {@link #acceptCorrected} — split counts feed the success summary and future audit. */
    public record AcceptCorrectedResult(int created, int verbatimRows, int correctedRows, List<SkippedRow> skipped) {

        /** True when every created row was accepted exactly as the AI proposed it. */
        public boolean allVerbatim() {
            return correctedRows == 0;
        }
    }

    /** Shared immutable state of one acceptance run (guards + artefacts), reused by both endpoints. */
    private record AcceptContext(
            Composition composition,
            java.util.Map<String, AIProposals> known,
            Set<String> knownRoles,
            Set<String> existingParts,
            ScoreFile scoreFile) {}

    /**
     * Accepts the listed proposal rows onto the composition (legacy contract — the range must be
     * identical to the AI's; any divergence is a 400 by design).
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

        AcceptContext ctx = prepareContext(compositionId, bandId, analysisId);
        Composition composition = ctx.composition();
        ScoreFile scoreFile = ctx.scoreFile();
        java.util.Map<String, AIProposals> known = ctx.known();
        Set<String> knownRoles = ctx.knownRoles();
        Set<String> existingParts = ctx.existingParts();

        List<SkippedRow> skipped = new ArrayList<>();
        int created = 0;
        for (AcceptedRow row : rows) {
            String normRole = normalize(row.role());
            if (normRole.isEmpty()) {
                throw new IllegalArgumentException("Wiersz bez roli nie może być zaakceptowany.");
            }
            AIProposals source = known.get(partKey(normRole, row.pageFrom(), row.pageTo()));
            if (source == null) {
                if (knownRoles.contains(normRole)) {
                    throw new IllegalArgumentException(
                            "Rola \"" + row.role() + "\" występuje w propozycjach analizy " + analysisId
                                    + ", ale nie dla stron " + row.pageFrom() + "–" + row.pageTo()
                                    + " — zaakceptuj wiersz o faktycznym zakresie z podglądu.");
                }
                throw new IllegalArgumentException(
                        "Rola \"" + row.role() + "\" nie występuje w propozycjach analizy " + analysisId + ".");
            }
            if (existingParts.contains(partKey(normRole, row.pageFrom(), row.pageTo()))) {
                skipped.add(new SkippedRow(row.role(),
                        "partia o tej roli i stronach już istnieje w utworze"));
                continue;
            }

            Instrument instrument = instrumentRepository.findByIdAndBandId(row.instrumentId(), bandId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Instrument " + row.instrumentId() + " nie należy do zespołu " + bandId + "."));

            // AI-proposal = wybór równy mapowaniu Score Analyzera: najpierw tag zespołu
            // (głos szczegółowy → tag ogólny), w razie braku/utraty tagu — nazwa głosu.
            Optional<Instrument> byRole = resolver.resolveInstrument(bandId, row.role());
            Optional<Instrument> resolved = byRole;
            if (source.teamTag() != null) {
                resolved = resolver.resolveInstrument(bandId, source.teamTag()).or(() -> byRole);
            }
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
            existingParts.add(partKey(normRole, row.pageFrom(), row.pageTo()));
            created++;
        }
        return new AcceptResult(created, skipped);
    }

    /**
     * Accepts proposal rows with user corrections (flagged feature M1 — „raport spójności”):
     * the page range no longer has to match the AI's verbatim.
     *
     * <p>Rules: each row is still anchored to a real proposal row of THIS analysis (role + AI range,
     * confidence stamped from the artefact); the corrected range must be sane and inside the score
     * file when its page count is known; an existing part at the FINAL role+range is SKIPPED like
     * in {@link #accept}. PartSource: range edited or instrument deviating from the resolver's
     * candidate → {@code HYBRID}; only an unedited range with the matching instrument stays {@code AI}.
     *
     * @throws IllegalArgumentException bad rows, ranges beyond the file, unknown role/instrument (→ 400)
     * @throws IllegalStateException cross-band / unfinished analysis / unreadable artefact (→ 409)
     */
    @Transactional
    public AcceptCorrectedResult acceptCorrected(Long compositionId, Long bandId, Long analysisId,
                                                 List<AcceptedRowCorrected> rows, String verifier) {
        if (compositionId == null || bandId == null || analysisId == null) {
            throw new IllegalArgumentException("compositionId, bandId i analysisId są wymagane.");
        }
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Lista akceptowanych wierszy jest pusta.");
        }
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("Akceptacja wymaga tożsamości użytkownika (US-4.5).");
        }

        AcceptContext ctx = prepareContext(compositionId, bandId, analysisId);
        List<SkippedRow> skipped = new ArrayList<>();
        int created = 0;
        int verbatimRows = 0;
        int correctedRows = 0;
        for (AcceptedRowCorrected row : rows) {
            String normRole = normalize(row.role());
            if (normRole.isEmpty()) {
                throw new IllegalArgumentException("Wiersz bez roli nie może być zaakceptowany.");
            }
            AIProposals source = ctx.known().get(partKey(normRole, row.aiPageFrom(), row.aiPageTo()));
            if (source == null) {
                if (ctx.knownRoles().contains(normRole)) {
                    throw new IllegalArgumentException(
                            "Rola \"" + row.role() + "\" występuje w propozycjach analizy " + analysisId
                                    + ", ale nie dla stron " + row.aiPageFrom() + "–" + row.aiPageTo()
                                    + " — zaakceptuj wiersz o faktycznym zakresie z podglądu.");
                }
                throw new IllegalArgumentException(
                        "Rola \"" + row.role() + "\" nie występuje w propozycjach analizy " + analysisId + ".");
            }

            int pageFrom = row.pageFrom();
            int pageTo = row.pageTo();
            if (pageFrom < 1 || pageTo < 1 || pageFrom > pageTo) {
                throw new IllegalArgumentException(
                        "Poprawiony zakres stron jest niedopuszczalny (" + pageFrom + "–" + pageTo + ").");
            }
            Integer pageCount = ctx.scoreFile().getPageCount();
            if (pageCount != null && (pageFrom > pageCount || pageTo > pageCount)) {
                throw new IllegalArgumentException(
                        "Zakres stron " + pageFrom + "–" + pageTo + " wykracza poza plik "
                                + "(plik ma " + pageCount + " stron).");
            }
            if (ctx.existingParts().contains(partKey(normRole, pageFrom, pageTo))) {
                skipped.add(new SkippedRow(row.role(),
                        "partia o tej roli i stronach już istnieje w utworze"));
                continue;
            }

            Optional<Instrument> byRole = resolver.resolveInstrument(bandId, row.role());
            Optional<Instrument> candidate = source.teamTag() != null
                    ? resolver.resolveInstrument(bandId, source.teamTag()).or(() -> byRole)
                    : byRole;

            Instrument instrument;
            if (row.instrumentId() != null) {
                instrument = instrumentRepository.findByIdAndBandId(row.instrumentId(), bandId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Instrument " + row.instrumentId() + " nie należy do zespołu " + bandId + "."));
            } else if (candidate.isPresent()) {
                instrument = candidate.get();
            } else {
                throw new IllegalArgumentException(
                        "Głos \"" + row.role() + "\" — wybierz instrument: mapowanie AI nie zwróciło kandydata.");
            }

            boolean rangeChanged = pageFrom != row.aiPageFrom() || pageTo != row.aiPageTo();
            boolean instrumentUnchanged = candidate.isPresent()
                    && candidate.get().getId().equals(instrument.getId());
            PartSource sourceValue = (rangeChanged || !instrumentUnchanged) ? PartSource.HYBRID : PartSource.AI;

            CompositionInstrument part = CompositionInstrument.forComposition(
                    ctx.composition(), instrument, row.role(), pageFrom, pageTo,
                    null, ctx.scoreFile(), sourceValue, source.confidence()); // artefact = jedno źródło pewności
            part.verify(verifier, Instant.now());
            partRepository.save(part);
            ctx.existingParts().add(partKey(normRole, pageFrom, pageTo));
            created++;
            if (sourceValue == PartSource.AI) {
                verbatimRows++;
            } else {
                correctedRows++;
            }
        }
        return new AcceptCorrectedResult(created, verbatimRows, correctedRows, skipped);
    }

    /** All guards + artefact loading shared by {@link #accept} and {@link #acceptCorrected}. */
    private AcceptContext prepareContext(Long compositionId, Long bandId, Long analysisId) {
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

        // REGRESJA (prod 2026-11-18, „Amerland”): arrangement.json bywa powtarzany dla tej samej
        // roli z różnymi zakresami stron („Puzon I B♭\" na str. 45 i 48 — dwa bloki tego samego
        // głosu). Poprzedni klucz po samej roli (putIfAbsent) trzymał tylko pierwszy wiersz, a
        // akceptacja któregoś z następnych sypała „Zakres stron nie zgadza się\" → HTTP 400.
        // Klucz musi więc identyfikować WIERZ: rola + zakres stron.
        java.util.Map<String, AIProposals> known = new java.util.HashMap<>();
        Set<String> knownRoles = new HashSet<>();
        for (AIProposals p : proposals) {
            String norm = normalize(p.role());
            known.putIfAbsent(partKey(norm, p.pageFrom(), p.pageTo()), p);
            if (!norm.isEmpty()) {
                knownRoles.add(norm);
            }
        }

        Set<String> existingParts = new HashSet<>();
        for (CompositionInstrument part : partRepository.findAllByComposition(composition)) {
            existingParts.add(partKey(normalize(part.getInstrumentRole()),
                    part.getPageFrom(), part.getPageTo()));
        }
        return new AcceptContext(composition, known, knownRoles, existingParts, scoreFile);
    }

    /** Composite identity of a voice block: normalized role + exact page range (see guards above). */
    private static String partKey(String normalizedRole, Object pageFrom, Object pageTo) {
        return normalizedRole + "|" + java.util.Objects.toString(pageFrom, "") + "-"
                + java.util.Objects.toString(pageTo, "");
    }

    private static String normalize(String s) {
        return s == null ? "" : RoleInstrumentResolver.normalize(s);
    }
}
