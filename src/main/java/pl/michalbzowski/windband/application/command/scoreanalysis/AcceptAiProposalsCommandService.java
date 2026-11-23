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
    private final ExcludePagesCommandService exclusionService;

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
    public record AcceptCorrectedResult(int created, int verbatimRows, int correctedRows,
                                        List<SkippedRow> skipped, List<SupersededPart> superseded,
                                        List<ExcludedScope> exclusionsCleared) {

        /** True when every created row was accepted exactly as the AI proposed it. */
        public boolean allVerbatim() {
            return correctedRows == 0;
        }
    }

    /**
     * Stary zakres partii nadpisanej korektą własnej partii w tym wezwaniu — klient usuwa go z
     * pokrycia stron, żeby odsłonięte strony (np. s. 14 po skróceniu 1–14 → 1–13) zostały lukami
     * do oznaczenia, a nie pozostawały „pokryte” przestarzałym zapisem.
     */
    public record SupersededPart(int pageFrom, int pageTo) {}

    /**
     * One voice block participating in this file's page space (see {@link #prepareContext}):
     * parts already saved in the composition carry a database id; blocks created or corrected
     * WITHIN one acceptance batch carry {@code id = null}. The guards treat both uniformly.
     */
    public record StoredPart(Long id, int pageFrom, int pageTo, String role) {}

    /** Shared immutable state of one acceptance run (guards + artefacts), reused by both endpoints. */
    private record AcceptContext(
            Composition composition,
            java.util.Map<String, AIProposals> known,
            Set<String> knownRoles,
            Set<String> existingParts,
            ScoreFile scoreFile,
            List<StoredPart> storedParts) {}

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
        List<StoredPart> createdNow = new ArrayList<>();
        Set<Long> updatedIds = new java.util.HashSet<>();
        List<ExcludedScope> clearedMarks = new ArrayList<>();
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

            // KOREKTA WŁASNEJ PARTII (prod 2026-11-21): re-akceptacja zakresu AI na głos, który
            // już ma partię w tym pliku — to aktualizacja jej zakresu, nie konflikt stron.
            CompositionInstrument own = updateOwnPartIfUnambiguous(ctx, updatedIds, createdNow,
                    normRole, row.role(), row.pageFrom(), row.pageTo(), instrument, verifier, null);
            if (own != null) {
                existingParts.add(partKey(normRole, row.pageFrom(), row.pageTo()));
                created++;
                clearExclusionsInto(ctx, clearedMarks, row.pageFrom(), row.pageTo());
                continue;
            }

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

            ensureNoPageOverlap(otherVoiceParts(ctx.storedParts(), updatedIds, normRole),
                    row.pageFrom(), row.pageTo());
            ensureNoPageOverlap(createdNow, row.pageFrom(), row.pageTo());

            CompositionInstrument part = CompositionInstrument.forComposition(
                    composition, instrument, row.role(), row.pageFrom(), row.pageTo(),
                    null, scoreFile, sourceValue, confidence);
            part.verify(verifier, Instant.now());
            partRepository.save(part);
            existingParts.add(partKey(normRole, row.pageFrom(), row.pageTo()));
            createdNow.add(new StoredPart(null, row.pageFrom(), row.pageTo(), row.role()));
            created++;
            clearExclusionsInto(ctx, clearedMarks, row.pageFrom(), row.pageTo());
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
        List<SupersededPart> superseded = new ArrayList<>();
        List<ExcludedScope> excludedCleared = new ArrayList<>();
        int created = 0;
        int verbatimRows = 0;
        int correctedRows = 0;
        List<StoredPart> createdNow = new ArrayList<>();
        Set<Long> updatedIds = new java.util.HashSet<>();
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

            // KOREKTA WŁASNEJ PARTII (regresja prod 2026-11-21, „Amerland”: zmiana zakresu
            // „Partytura” 1–14 → 1–13 odpalała 400 „nachodzi na partię ... („Partytura”)”).
            // Poprawka głosu użytkownika jest AKTUALIZACJĄ jego partii, nie konfliktem stron.
            CompositionInstrument own = updateOwnPartIfUnambiguous(ctx, updatedIds, createdNow,
                    normRole, row.role(), pageFrom, pageTo, instrument, verifier, superseded);
            if (own != null) {
                ctx.existingParts().add(partKey(normRole, pageFrom, pageTo));
                created++;
                correctedRows++; // przyjęcie z modyfikacją = audit „z korektą”
                clearExclusionsInto(ctx, excludedCleared, pageFrom, pageTo);
                continue;
            }

            boolean rangeChanged = pageFrom != row.aiPageFrom() || pageTo != row.aiPageTo();
            boolean instrumentUnchanged = candidate.isPresent()
                    && candidate.get().getId().equals(instrument.getId());
            PartSource sourceValue = (rangeChanged || !instrumentUnchanged) ? PartSource.HYBRID : PartSource.AI;

            ensureNoPageOverlap(otherVoiceParts(ctx.storedParts(), updatedIds, normRole), pageFrom, pageTo);
            ensureNoPageOverlap(createdNow, pageFrom, pageTo);

            CompositionInstrument part = CompositionInstrument.forComposition(
                    ctx.composition(), instrument, row.role(), pageFrom, pageTo,
                    null, ctx.scoreFile(), sourceValue, source.confidence()); // artefact = jedno źródło pewności
            part.verify(verifier, Instant.now());
            partRepository.save(part);
            ctx.existingParts().add(partKey(normRole, pageFrom, pageTo));
            createdNow.add(new StoredPart(null, pageFrom, pageTo, row.role()));
            created++;
            clearExclusionsInto(ctx, excludedCleared, pageFrom, pageTo);
            if (sourceValue == PartSource.AI) {
                verbatimRows++;
            } else {
                correctedRows++;
            }
        }
        return new AcceptCorrectedResult(created, verbatimRows, correctedRows, skipped, superseded, excludedCleared);
    }

    /**
     * Twarza walidacja (reguła domeny, patrz {@code ProposalConsistency OVERLAP}): jedna strona nut
     * może należeć do jednego głosu. Zakres kolidujący z dowolną inną partią utworu — także tą z
     * wcześniejszej analizy lub przyjętą w tej samej kolejce — jest odrzucany, zanim cokolwiek trafi
     * do bazy. Wyjątek: korekta WŁASNEJ partii głosu (patrz {@link #updateOwnPartIfUnambiguous}).
     */
    private static void ensureNoPageOverlap(List<StoredPart> existing, int pageFrom, int pageTo) {
        for (StoredPart r : existing) {
            if (overlaps(pageFrom, pageTo, r.pageFrom(), r.pageTo())) {
                String who = r.role() == null || r.role().isBlank() ? "" : " („" + r.role() + "”)";
                throw new IllegalArgumentException(
                        "Zakres stron " + pageFrom + "–" + pageTo
                                + " nachodzi na partię str. " + r.pageFrom() + "–" + r.pageTo() + who
                                + " — strona może należeć do jednego głosu.");
            }
        }
    }

    /**
     * Korekta własnej partii (regresja prod 2026-11-21, „Amerland”): AI wyznaczyła głos np. na
     * strony 1–14, użytkownik skraca do 1–13 — a stara partia tej samej roli (1–14) w tym samym
     * pliku blokowała akceptację komunikatem „nachodzi na partię ... („Partytura”)”. Poprawka przez
     * użytkownika to AKTUALIZACJA zakresu jego partii, nie konflikt stron.
     *
     * <p>Zasady: dokładnie jedna partia tej roli (tego pliku) nachodząca na nowy zakres →
     * {@code updateMapping} (waliduje inwarianse jak factory i rozwalnia zamarznięty audyt
     * US-6.5; świeży {@code verify} zamarza parę ponownie — US-4.5) + zapis; zwraca partię.
     * Więcej niż jedna taka partia → nie da się rozstrzygnąć, którą poprawiasz → IAE (400).
     * Brak takich partii → {@code null} (normalny tor: utworzenie nowej partii).
     */
    private CompositionInstrument updateOwnPartIfUnambiguous(
            AcceptContext ctx, Set<Long> updatedIds, List<StoredPart> createdNow,
            String normRole, String displayRole, int pageFrom, int pageTo,
            Instrument instrument, String verifier, List<SupersededPart> superseded) {
        List<StoredPart> own = new ArrayList<>();
        for (StoredPart p : ctx.storedParts()) {
            if (p.id() != null && normalize(p.role()).equals(normRole)
                    && overlaps(p.pageFrom(), p.pageTo(), pageFrom, pageTo)) {
                own.add(p);
            }
        }
        if (own.size() > 1) {
            throw new IllegalArgumentException(
                    "Głos \"" + displayRole + "\" ma w tym pliku więcej niż jedną partię nachodzącą"
                            + " na strony " + pageFrom + "–" + pageTo
                            + " — nie da się rozstrzygnąć, którą chcesz poprawić.");
        }
        if (own.isEmpty()) {
            return null;
        }
        CompositionInstrument target = partRepository.findById(own.get(0).id())
                .orElseThrow(() -> new IllegalStateException(
                        "Partia o id " + own.get(0).id() + " zniknęła podczas akceptacji."));
        ensureNoPageOverlap(createdNow, pageFrom, pageTo); // głosy przyjęte wcześniej w kolejce są święte
        target.updateMapping(instrument, displayRole, pageFrom, pageTo, ctx.scoreFile());
        target.verify(verifier, Instant.now());
        partRepository.save(target);
        updatedIds.add(own.get(0).id());
        createdNow.add(new StoredPart(null, pageFrom, pageTo, displayRole));
        if (superseded != null) {
            // Stary zakres tej samej partii nie pokrywa już stron — klient odsłania je jako lukę.
            superseded.add(new SupersededPart(own.get(0).pageFrom(), own.get(0).pageTo()));
        }
        return target;
    }

    /** Parts of OTHER voices in this file: the page-conflict set for a given role (guards above). */
    private static List<StoredPart> otherVoiceParts(List<StoredPart> stored, Set<Long> updatedIds,
            String normRole) {
        List<StoredPart> out = new ArrayList<>();
        for (StoredPart p : stored) {
            if (!updatedIds.contains(p.id()) && !normalize(p.role()).equals(normRole)) {
                out.add(p);
            }
        }
        return out;
    }

    private static boolean overlaps(int fromA, int toA, int fromB, int toB) {
        return Math.max(fromA, fromB) <= Math.min(toA, toB);
    }

    /**
     * Karta luki (widok verify-ai): user widzi, że strony nie należą do żadnego głosu po skróconym
     * zakresie innego głosu (np. partytura 1–14 → 1–13 zwiastuje, że strona 14 czeka na oznaczenie).
     * Oznaczenie tworzy normalna partię utworu: rola = nazwa wybranego instrumentu z zespołu, źródło
     * HYBRID (ludzka decyzja), confidence 1.0 (brak artefaktu AI dla tej decyzji).
     *
     * <p>Zasady: zakres musi być legalny i mieścić się w pliku nut; dokładny duplikat zakresu
     * (nawet pod inną rolą) jest pomijany; cokolwiek pokrywające się tylko częściowo → 400.
     */
    @Transactional
    public AcceptCorrectedResult assignGapPages(Long compositionId, Long bandId, Long analysisId,
            int pageFrom, int pageTo, Long instrumentId, String verifier) {
        if (compositionId == null || bandId == null || analysisId == null) {
            throw new IllegalArgumentException("compositionId, bandId i analysisId są wymagane.");
        }
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("Oznaczenie luki wymaga tożsamości użytkownika (US-4.5).");
        }
        if (pageFrom < 1 || pageTo < 1 || pageFrom > pageTo) {
            throw new IllegalArgumentException(
                    "Zakres stron jest niedopuszczalny (" + pageFrom + "–" + pageTo + ").");
        }

        AcceptContext ctx = prepareContext(compositionId, bandId, analysisId);
        Integer pageCount = ctx.scoreFile().getPageCount();
        if (pageCount != null && (pageFrom > pageCount || pageTo > pageCount)) {
            throw new IllegalArgumentException(
                    "Zakres stron " + pageFrom + "–" + pageTo + " wykracza poza plik "
                            + "(plik ma " + pageCount + " stron).");
        }

        Instrument instrument = instrumentId == null
                ? null
                : instrumentRepository.findByIdAndBandId(instrumentId, bandId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Wybierz instrument z zespołu " + bandId
                                        + ", którego partia ma objąć te strony."))
                ;
        if (instrument == null) {
            throw new IllegalArgumentException(
                    "Oznaczenie luki wymaga wyboru instrumentu z zespołu.");
        }

        // dokładny duplikat zakresu = ta sama strona już oznaczona → skip zamiast błędu;
        // częściowe nachodzenie = sprzeczność → 400 (por. ensureNoPageOverlap). Deliberately NO
        // self-update here: the user chose an instrument for a free page — rewriting a bigger part
        // of that instrument would destroy its range, which is never the intent of a gap label.
        for (StoredPart r : ctx.storedParts()) {
            if (r.pageFrom() == pageFrom && r.pageTo() == pageTo) {
                return new AcceptCorrectedResult(0, 0, 0,
                        List.of(new SkippedRow(instrument.getName(),
                                "partia na stronach " + pageFrom + "–" + pageTo
                                        + " już istnieje w utworze")),
                        List.of(),
                        List.of());
            }
        }
        ensureNoPageOverlap(ctx.storedParts(), pageFrom, pageTo);

        CompositionInstrument part = CompositionInstrument.forComposition(
                ctx.composition(), instrument, instrument.getName(), pageFrom, pageTo,
                null, ctx.scoreFile(), PartSource.HYBRID, 1.0d);
        part.verify(verifier, Instant.now());
        partRepository.save(part);
        // Strony są teraz zajęte — znaczenie „nie wykorzystam” w tym zakresie traci sens (auto-odznaczenie).
        List<ExcludedScope> cleared =
                exclusionService.removeOverlap(ctx.composition(), ctx.scoreFile(), pageFrom, pageTo);
        return new AcceptCorrectedResult(1, 0, 1, List.of(), List.of(), cleared);
    }

    /**
     * Przyjęcie (w jakiejkolwiek formie) zajmuje strony — unieważnia znaczenia „nie wykorzystam”,
     * które z tym zakresem nachodzą (split wewnątrz {@link ExcludePagesCommandService}).
     */
    private void clearExclusionsInto(AcceptContext ctx, List<ExcludedScope> sink,
                                     int pageFrom, int pageTo) {
        sink.addAll(exclusionService.removeOverlap(ctx.composition(), ctx.scoreFile(), pageFrom, pageTo));
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
        List<StoredPart> storedParts = new ArrayList<>();
        Long fileId = scoreFile.getId();
        for (CompositionInstrument part : partRepository.findAllByComposition(composition)) {
            int from = part.getPageFrom() == null ? 0 : part.getPageFrom();
            int to = part.getPageTo() == null ? 0 : part.getPageTo();
            if (from <= 0 || to <= 0) {
                continue; // legacy row without pages — takes no part of the pages model
            }
            ScoreFile partFile = part.getScoreFile();
            if (partFile != null && fileId != null && !fileId.equals(partFile.getId())) {
                // inny plik nut = własna przestrzeń stron — nie nachodzi na strony TEGO pliku
                continue;
            }
            existingParts.add(partKey(normalize(part.getInstrumentRole()), from, to));
            storedParts.add(new StoredPart(part.getId(), from, to, part.getInstrumentRole()));
        }
        return new AcceptContext(composition, known, knownRoles, existingParts, scoreFile, storedParts);
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
