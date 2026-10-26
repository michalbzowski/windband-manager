package pl.michalbzowski.windband.application.query.scoreanalysis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;

/**
 * US-4.2 — read side for downloading an AI-analysis artefact (arrangement.json / .musicxml /
 * .mid / validation.txt) of a composition's LATEST analysis, band-isolated exactly like the
 * score-file download (see {@code ScoreFileDownloadQueryService} for the two-layer contract):
 *
 * <ul>
 *   <li>{@link IllegalArgumentException} — band missing, composition not found in this band
 *       (cross-band reads refuse to leak existence), or unknown artefact name (→ HTTP 400);</li>
 *   <li>{@link IllegalStateException} — no analysis, non-successful phase, unrecorded path, or the artefact
 *       file missing on disk (→ HTTP 409).</li>
 * </ul>
 *
 * The stored per-row path is the source of truth (artefacts written by a previous build or an earlier
 * layout root still resolve as long as the file exists); no re-derivation through AiArtifactsLayout.
 */
@Service
public class ScoreAnalysisArtifactQueryService {

    private final BandQueryService bandQueryService;
    private final CompositionRepository compositionRepository;
    private final ScoreAnalysisRepository analysisRepository;

    public ScoreAnalysisArtifactQueryService(BandQueryService bandQueryService,
                                             CompositionRepository compositionRepository,
                                             ScoreAnalysisRepository analysisRepository) {
        this.bandQueryService = bandQueryService;
        this.compositionRepository = compositionRepository;
        this.analysisRepository = analysisRepository;
    }

    /** A resolved artefact: on-disk path plus the scalar metadata the download endpoint needs. */
    public record Artifact(Path path, String fileName, MediaType mediaType) { }

    /**
     * Resolve one named artefact of the latest analysis for this band-scoped composition.
     *
     * @param artifact one of: {@code json}, {@code musicxml}, {@code mid}, {@code txt} (case-insensitive)
     */
    @Transactional(readOnly = true)
    public Artifact open(Long compositionId, Long bandId, String artifact) {
        // Layer 1 — the band must exist (IllegalArgumentException → 400 via GlobalExceptionHandler).
        bandQueryService.getRequiredBand(bandId);

        // The repository port only offers band-scoped reads (no unscoped findById): a cross-band or
        // unknown composition both surface as IAE → 400 without leaking which case it was.
        compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Utwór " + compositionId + " nie został znaleziony w zespole " + bandId + "."));

        ScoreAnalysis analysis = analysisRepository.findLatestByCompositionIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException("Brak analizy AI dla utworu " + compositionId + "."));
        if (analysis.getPhase() != ScoreAnalysis.Phase.SUCCEEDED) {
            throw new IllegalStateException(
                    "Analiza nie została zakończona sukcesem (faza: " + phaseName(analysis) + ") — wynik nie jest dostępny.");
        }

        String ext = artifact == null ? "" : artifact.trim().toLowerCase(Locale.ROOT);
        // Getter chains are inlined (instead of an abstracting helper) so null handling stays visible to static analysis:
        // unknown name → 400; recorded-but-null/blank value → 409.
        String raw;
        if ("json".equals(ext)) {
            raw = analysis.getArrangementJsonPath();
        } else if ("musicxml".equals(ext)) {
            raw = analysis.getArrangementMusicxmlPath();
        } else if ("mid".equals(ext)) {
            raw = analysis.getArrangementMidPath();
        } else if ("txt".equals(ext)) {
            raw = analysis.getValidationTxtPath();
        } else {
            throw new IllegalArgumentException("Nieznany artefakt: " + ext
                    + " (dopuszczalne: json, musicxml, mid, txt).");
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Artefakt \"" + ext + "\" nie został wygenerowany dla analizy " + analysis.getId() + ".");
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            throw new IllegalStateException("Ścieżka artefaktu \"" + ext + "\" jest wadliwa (nie-jawna): " + raw);
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("Artefakt \"" + ext + "\" analizy " + analysis.getId()
                    + " nie istnieje na dysku.");
        }
        MediaType mediaType = "json".equals(ext) ? new MediaType("application", "json")
                : "musicxml".equals(ext) ? new MediaType("application", "xml")
                        : "mid".equals(ext) ? new MediaType("audio", "midi")
                                : MediaType.TEXT_PLAIN;
        return new Artifact(path, path.getFileName().toString(), mediaType);
    }

    private static String phaseName(ScoreAnalysis analysis) {
        return analysis.getPhase() == null ? "null" : analysis.getPhase().name();
    }
}
