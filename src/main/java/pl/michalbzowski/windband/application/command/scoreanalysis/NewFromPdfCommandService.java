package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.composition.ScoreFileCommandService;
import pl.michalbzowski.windband.application.command.composition.ScoreFileUploadRequest;
import pl.michalbzowski.windband.application.dto.composition.ScoreFileDto;
import pl.michalbzowski.windband.application.query.llmkey.LlmApiKeyQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.llmkey.LlmProvider;

/**
 * Faza D — "New composition from PDF": the one-flow path (goal #2 of the plan) in which a user
 * uploads a score PDF, the analyzer suggests voices + work metadata, and confirmation materializes
 * the library entry.
 *
 * <p>{@link #start} is deliberately transactional end-to-end: draft composition + stored file +
 * spawned analysis live or die together (a rolled-back start leaves no orphan draft).
 * {@link #confirm} applies the human-edited work texts and routes the accepted rows through the
 * SAME acceptance kernel as the detail-page flow ({@link AcceptAiProposalsCommandService}) —
 * one code path, one set of invariants.
 */
@Service
@RequiredArgsConstructor
public class NewFromPdfCommandService {

    private final BandRepository bandRepository;
    private final CompositionRepository compositionRepository;
    private final ScoreFileCommandService scoreFileUpload;
    private final ScoreAnalysisCommandService analysisStart;
    private final ScoreAnalysisRepository analysisRepository;
    private final LlmApiKeyQueryService llmKeyQueryService;
    private final AcceptAiProposalsCommandService acceptService;

    /** Outcome of the PDF upload + analysis start — everything the UI needs to poll. */
    public record StartResult(Long compositionId, Long fileId, Long analysisId) {}

    public record ConfirmResult(Long compositionId, int partsCreated) {}

    /**
     * Step 1 — create the draft entry, store the PDF as its score file, kick off the analysis.
     *
     * @param titleOrNull preferred title; falls back to the file name (no extension), then "Nowy utwór"
     * @throws IllegalArgumentException empty bytes / missing band (→ 400)
     */
    @Transactional
    public StartResult start(byte[] bytes, String contentType, String fileName,
                             String titleOrNull, Long bandId, Long userId) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Brak treści pliku do zapisu.");
        }
        if (bandId == null) {
            throw new IllegalArgumentException("bandId jest wymagany.");
        }
        if (!"application/pdf".equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                    "Ten flow akceptuje tylko pliki PDF (otrzymano: " + contentType + ").");
        }
        Band band = bandRepository.findById(bandId)
                .orElseThrow(() -> new IllegalStateException("Zespół " + bandId + " nie znaleziony."));

        String title = (titleOrNull != null && !titleOrNull.isBlank())
                ? titleOrNull.trim()
                : defaultTitleFromFileName(fileName);
        Composition composition = compositionRepository.save(Composition.create(title, "", "", "", band));

        ScoreFileDto storedFile = scoreFileUpload.upload(
                new ScoreFileUploadRequest(fileName, contentType, bytes),
                composition.getId(), bandId);

        // Optional user OpenRouter key from the vault (Phase C) — server-side provider when absent.
        String llmApiKey = userId == null
                ? null
                : llmKeyQueryService.plainKey(userId, LlmProvider.OPENROUTER).orElse(null);
        Long analysisId = analysisStart.start(storedFile.fileId(), bandId, llmApiKey);
        return new StartResult(composition.getId(), storedFile.fileId(), analysisId);
    }

    /**
     * Step 2 — apply the human-edited work metadata and accept the chosen proposal rows.
     *
     * @throws IllegalArgumentException title blank / unknown role (→ 400)
     * @throws IllegalStateException cross-band composition or unfinished analysis with rows (→ 409)
     */
    @Transactional
    public ConfirmResult confirm(Long compositionId, Long bandId, String title,
                                 String composerOrNull, String arrangerOrNull,
                                 List<AcceptAiProposalsCommandService.AcceptedRow> rows,
                                 String verifier) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Tytuł utworu jest wymagany.");
        }
        if (compositionId == null || bandId == null) {
            throw new IllegalArgumentException("compositionId i bandId są wymagane.");
        }
        Composition composition = compositionRepository.findByIdAndBandId(compositionId, bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + compositionId + " nie należy do zespołu " + bandId + "."));

        int created = 0;
        if (rows != null && !rows.isEmpty()) {
            Optional<ScoreAnalysis> latestSucceeded = analysisRepository
                    .findFirstByCompositionIdOrderByIdDesc(compositionId)
                    .filter(a -> a.getPhase() == ScoreAnalysis.Phase.SUCCEEDED);
            if (latestSucceeded.isEmpty()) {
                throw new IllegalStateException(
                        "Analiza AI utworu nie została pomyślnie zakończona — partie nie mogą być zaakceptowane.");
            }
            AcceptAiProposalsCommandService.AcceptResult result = acceptService.accept(
                    compositionId, bandId, latestSucceeded.get().getId(), rows, verifier);
            created = result.created();
        }

        // updateTexts(null) keeps the current value — composer/arranger are optional fields.
        composition.updateTexts(title.trim(), null, composerOrNull, arrangerOrNull);
        return new ConfirmResult(composition.getId(), created);
    }

    /** "zadzwonil_polonez.pdf" → "zadzwonil_polonez"; junk/blank inputs → "Nowy utwór". */
    private static String defaultTitleFromFileName(String fileName) {
        if (fileName != null && !fileName.isBlank()) {
            String base = fileName.trim();
            int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
            if (slash >= 0) {
                base = base.substring(slash + 1);
            }
            int dot = base.lastIndexOf('.');
            if (dot > 0) {
                base = base.substring(0, dot);
            }
            if (!base.isBlank()) {
                return base;
            }
        }
        return "Nowy utwór";
    }
}
