package pl.michalbzowski.windband.application.command.scoreanalysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusion;
import pl.michalbzowski.windband.domain.composition.ScorePageExclusionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Oznaczenia stron „nie wykorzystam" w raporcie spójności AI: zapisywanie zakresów
 * (kolejność: guardy → algebra zakresów → persystencja) i ich usuwanie z dzieleniem
 * przy częściowym nachodzeniu („Przywróć" środkowego kawałka zostawia oba ogony).
 *
 * <p>Reguły biznesowe (kontrakt API: IAE→400, patrz GlobalExceptionHandler):
 * <ul>
 *   <li>znaczenie może pokrywać TYLKO strony wolne — strony objęte przyjętą partią
 *       pliku nie mogą zostać oznaczone (wzajemna wykluczalność: strona jest albo w
 *       głosie, albo nieużywana);</li>
 *   <li>dodanie zakresu nachodzącego/ściskającego istniejące znaczenia SCALA je w
 *       jeden wiersz (autor+chwila = nowy pisarz — widoczne w sekcji audytu);</li>
 *   <li>usuwanie w zakresie dzieli częściowo nachodzące wiersze: ogony zostają z
 *       parą audytową oryginału, a unieważnione przedziały wracają do kolejki luk;</li>
 *   <li>akceptacja partii (assign/accept) wywołuje to samo usuwanie — nowsza,
 *       silniejsza intencja wygrywa automatycznie.</li>
 * </ul>
 *
 * <p>Logika dzielenia i scalania zakresów należy do serwisu (pokrywają ją testy jednostkowe);
 * guard {@link #verifyRange} ma postać metody statycznej dla wygodnych mutacji w PIT.
 */
@Service
public class ExcludePagesCommandService {

    private final ScorePageExclusionRepository exclusionRepository;
    private final CompositionInstrumentRepository partRepository;
    private final CompositionRepository compositionRepository;
    private final ScoreAnalysisRepository analysisRepository;
    private final ScoreFileRepository scoreFileRepository;

    public ExcludePagesCommandService(ScorePageExclusionRepository exclusionRepository,
                                      CompositionInstrumentRepository partRepository,
                                      CompositionRepository compositionRepository,
                                      ScoreAnalysisRepository analysisRepository,
                                      ScoreFileRepository scoreFileRepository) {
        this.exclusionRepository = exclusionRepository;
        this.partRepository = partRepository;
        this.compositionRepository = compositionRepository;
        this.analysisRepository = analysisRepository;
        this.scoreFileRepository = scoreFileRepository;
    }

    // ── Signatures (REST: id + isolation guard) ─────────────────────────────────

    /** REST form: resolves a file + composition from an analysis, marks the range as unused. */
    @Transactional
    public ExcludedScope markUnusedByIds(Long bandId, Long compositionId, Long analysisId,
                                         int pageFrom, int pageTo, String markedBy) {
        Ctx ctx = resolveContext(bandId, compositionId, analysisId);
        return markUnused(ctx.composition(), ctx.scoreFile(), pageFrom, pageTo, markedBy);
    }

    /** REST form: resolves the context and clears overlaps in the range (see {@link #removeOverlap}). */
    @Transactional
    public List<ExcludedScope> restoreByIds(Long bandId, Long compositionId, Long analysisId,
                                            int pageFrom, int pageTo) {
        Ctx ctx = resolveContext(bandId, compositionId, analysisId);
        return removeOverlap(ctx.composition(), ctx.scoreFile(), pageFrom, pageTo);
    }

    /**
     * The context guards are the same as {@code AcceptAiProposalsCommandService.prepareContext}:
     * a cross-band id is ISE→409, and an unknown one — the appropriate status message.
     */
    private Ctx resolveContext(Long bandId, Long compositionId, Long analysisId) {
        if (bandId == null || compositionId == null || analysisId == null) {
            throw new IllegalArgumentException("Zespół, utwór i analiza są wymagane.");
        }
        Composition composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + compositionId + " nie należy do zespołu " + bandId + "."));
        ScoreAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalStateException("Analiza " + analysisId + " nie znaleziona."));
        if (!composition.getId().equals(analysis.getComposition().getId())) {
            throw new IllegalStateException("Analiza " + analysisId + " nie należy do utworu " + compositionId + ".");
        }
        ScoreFile scoreFile = scoreFileRepository.findById(analysis.getScoreFileId())
                .orElseThrow(() -> new IllegalStateException(
                        "Plik nut analizy " + analysisId + " nie istnieje."));
        return new Ctx(composition, scoreFile);
    }

    private record Ctx(Composition composition, ScoreFile scoreFile) {}

    // ── Signatures ─────────────────────────────────────────────────────────────

    /**
     * Oznacza zakres stron pliku jako nieużywany; zakres nachodzący na istniejące znaczenie
     * scala je w jeden wiersz (audyt = nowy pisarz). Strony pokryte
     * częścią → {@link IllegalArgumentException} (IAE→400).
     */
    @Transactional
    public ExcludedScope markUnused(Composition composition, ScoreFile scoreFile,
                                    int pageFrom, int pageTo, String markedBy) {
        verifyRange(pageFrom, pageTo);
        requireSameComposition(composition, scoreFile);
        requireWithinPageCount(scoreFile, pageFrom, pageTo);
        requirePagesFreeOfParts(composition, scoreFile, pageFrom, pageTo);

        List<ScorePageExclusion> existing = sortedOf(scoreFile);
        int from = pageFrom;
        int to = pageTo;
        List<ScorePageExclusion> absorbed = new ArrayList<>();
        for (ScorePageExclusion row : existing) {
            // Nachodzenie LUB dotykanie — sąsiednie znaczenie wchłaniamy, żeby raport był jednym blokiem.
            if (row.getPageFrom() <= to + 1 && row.getPageTo() >= from - 1) {
                absorbed.add(row);
                from = Math.min(from, row.getPageFrom());
                to = Math.max(to, row.getPageTo());
            }
        }
        for (ScorePageExclusion row : absorbed) {
            exclusionRepository.delete(row);
        }
        ScorePageExclusion created = exclusionRepository.save(ScorePageExclusion.mark(
                composition, scoreFile, from, to, markedBy, Instant.now()));
        return new ExcludedScope(created.getPageFrom(), created.getPageTo());
    }

    /**
     * Usuwa znaczenie objęte w zakresie (czyści: tag/akceptacja zajmie te strony).
     * Częściowo nachodzące wiersze dzielą się — ogony zostają z parą audytową
     * oryginału. Zwraca unieważnione przedziały (przekrój z zakresem) do podsumowania
     * dla użytkownika („zakres unieważniony — strony znów wolne").
     */
    @Transactional
    public List<ExcludedScope> removeOverlap(Composition composition, ScoreFile scoreFile,
                                             int pageFrom, int pageTo) {
        if (composition == null || scoreFile == null) {
            throw new IllegalArgumentException("Kompozycja i plik nut są wymagane");
        }
        verifyRange(pageFrom, pageTo);
        requireSameComposition(composition, scoreFile);

        List<ExcludedScope> cleared = new ArrayList<>();
        for (ScorePageExclusion row : sortedOf(scoreFile)) {
            int a = row.getPageFrom();
            int b = row.getPageTo();
            if (b < pageFrom || a > pageTo) {
                continue; // bez nachodzenia — znaczenie nie dotknięte
            }
            cleared.add(new ExcludedScope(Math.max(a, pageFrom), Math.min(b, pageTo)));
            exclusionRepository.delete(row);
            if (a < pageFrom) {
                exclusionRepository.save(ScorePageExclusion.mark(
                        composition, scoreFile, a, pageFrom - 1, row.getMarkedBy(), row.getMarkedAt()));
            }
            if (b > pageTo) {
                exclusionRepository.save(ScorePageExclusion.mark(
                        composition, scoreFile, pageTo + 1, b, row.getMarkedBy(), row.getMarkedAt()));
            }
        }
        return cleared;
    }

    /** Czytelna lista znaczeń pliku (ogon → początek), dla sekcji widoku. */
    @Transactional(readOnly = true)
    public List<ScorePageExclusion> listFor(ScoreFile scoreFile) {
        if (scoreFile == null) {
            return List.of();
        }
        return sortedOf(scoreFile);
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    static void verifyRange(int pageFrom, int pageTo) {
        if (pageFrom < 1) {
            throw new IllegalArgumentException(
                    "Pierwsza strona zakresu nie może być mniejsza niż 1 (otrzymano: " + pageFrom + ")");
        }
        if (pageTo < pageFrom) {
            throw new IllegalArgumentException(
                    "Zakres stron jest odwrócony (od=" + pageFrom + ", do=" + pageTo + ").");
        }
    }

    /** Strony znaczenia muszą mieścić się w pliku nut, którego oznaczamy. */
    static void requireWithinPageCount(ScoreFile scoreFile, int pageFrom, int pageTo) {
        Integer pageCount = scoreFile.getPageCount();
        if (pageCount != null && pageTo > pageCount) {
            throw new IllegalArgumentException(
                    "Zakres stron wykracza poza plik nut (plik ma " + pageCount + " stron).");
        }
    }

    static void requireSameComposition(Composition composition, ScoreFile scoreFile) {
        if (composition == null || scoreFile == null) {
            throw new IllegalArgumentException("Kompozycja i plik nut są wymagane");
        }
        if (!composition.getId().equals(scoreFile.getComposition().getId())) {
            throw new IllegalArgumentException("Plik nut nie należy do tej kompozycji — strony to inny plik");
        }
    }

    private void requirePagesFreeOfParts(Composition composition, ScoreFile scoreFile,
                                         int pageFrom, int pageTo) {
        Long fileId = scoreFile.getId();
        for (CompositionInstrument part : partRepository.findAllByComposition(composition)) {
            if (!fileId.equals(part.getScoreFile() == null ? null : part.getScoreFile().getId())) {
                continue; // część innego pliku (lub legacy bez pliku) — inna przestrzeń stron
            }
            if (part.getPageFrom() <= pageTo && part.getPageTo() >= pageFrom) {
                throw new IllegalArgumentException(
                        "Strony " + pageFrom + "–" + pageTo + " są już w głosie \"" + part.getInstrumentRole()
                                + "\" (str. " + part.getPageFrom() + "–" + part.getPageTo() + "). "
                                + "Wybierz wolne strony albo najpierw skoryguj zakres tego głosu.");
            }
        }
    }

    private List<ScorePageExclusion> sortedOf(ScoreFile scoreFile) {
        return exclusionRepository.findAllByScoreFile(scoreFile).stream()
                .sorted(Comparator.comparingInt(ScorePageExclusion::getPageFrom))
                .toList();
    }
}
