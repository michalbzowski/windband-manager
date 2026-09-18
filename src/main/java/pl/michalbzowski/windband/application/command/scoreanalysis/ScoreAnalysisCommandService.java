package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysis;
import pl.michalbzowski.windband.domain.composition.ScoreAnalysisRepository;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;

/**
 * US-4.1 — orchestrate AI-analysis of a score file, persisting its lifecycle in the
 * {@code score_analysis} row.
 *
 * <p>Steps (one {@link #start} call):
 * <ol>
 *   <li>Band-isolate: resolve the composition through {@code findByIdAndBandId}, and the
 *       score file by PK + band-scoped check. Either failure throws IllegalStateException → 409.</li>
 *   <li>Create a PENDING row (persisted first so we have an id to use as the output-dir name).</li>
 *   <li>Ask the {@link AiAnalysisRunner} to spawn the pipeline. If the runner is not configured,
 *       the service leaves the row in FAILED with a clear Polish message and re-throws mapped
 *       as 501 by the GlobalExceptionHandler (or 500 — the test suite is tolerant).</li>
 *   <li>Once the runner acknowledges, transition the row PENDING → RUNNING (persisted),
 *       and a follow-on endpoint (out of scope for US-4.1 — see the plan) will poll via
 *       {@link AiAnalysisRunner#inspect(String)} and apply SUCCEEDED/FAILED terminal transitions.</li>
 * </ol>
 *
 * <p>Band isolation: every read is through the composition; the score-file FK is
 * re-checked against {@code composition.getId()} (a file from a foreign band cannot be
 * "borrowed" for a US-4.1 run).
 */
@Service
@RequiredArgsConstructor
public class ScoreAnalysisCommandService {

    private final CompositionRepository compositionRepository;
    private final ScoreFileRepository scoreFileRepository;
    private final ScoreAnalysisRepository analysisRepository;
    private final AiAnalysisRunner runner;
    private final AiArtifactsLayout layout;    // owns the on-disk outputDir for each run

    /**
     * Spawns an AI-analysis of the given score file bound to the given band.
     *
     * @return the id of the newly created {@link ScoreAnalysis} row
     * @throws IllegalStateException cross-band, missing composition or score file (→ 409)
     * @throws AiAnalysisRunner.RunnerNotConfiguredException pipeline not available (→ 501/500)
     */
    @Transactional
    public Long start(Long scoreFileId, Long bandId) {
        if (scoreFileId == null) throw new IllegalArgumentException("scoreFileId required");
        if (bandId == null) throw new IllegalArgumentException("bandId required");

        ScoreFile file = scoreFileRepository.findById(scoreFileId).orElseThrow(
                () -> new IllegalStateException("ScoreFile " + scoreFileId + " nie znaleziony."));

        // Band isolation: the composition of this file must resolve in THIS band.
        Composition comp = compositionRepository.findByIdAndBandId(file.getComposition().getId(), bandId)
                .orElseThrow(() -> new IllegalStateException(
                        "Utwór " + file.getComposition().getId() + " nie należy do zespołu " + bandId + "."));

        // Reject ZIP parents — only standalone PDFs are analysable in US-4.1 MVP scope.
        if (!"application/pdf".equalsIgnoreCase(file.getMimeType())) {
            throw new IllegalArgumentException("Analiza AI obsługuje tylko pliki PDF (id " + scoreFileId + ")");
        }

        // 1. Create the PENDING row — persisted FIRST so we have a stable id for the output dir.
        ScoreAnalysis pending = ScoreAnalysis.pending(comp, file.getId());
        ScoreAnalysis saved = analysisRepository.save(pending);
        Long analysisId = saved.getId();

        // 2. Compute the output dir (deterministic from the analysis id — easy to clean up later).
        Path outputDir = layout.outputFor(analysisId);

        // 3. Spawn the pipeline. The runner may throw:
        //      - RunnerNotConfiguredException  → propagates as-is (HTTP 501/500)
        //      - IllegalArgumentException      → mark FAILED with the message
        String runnerRef;
        try {
            runnerRef = runner.start(new AiAnalysisRunner.AnalysisRequest(Path.of(file.getStoragePath()), outputDir));
        } catch (AiAnalysisRunner.RunnerNotConfiguredException rc) {
            // Leave the row in PENDING (we never transitioned it), but give the user a
            // clear DB-visible error — better than an opaque 501 with no trace.
            analysisRepository.save(saved.fail(rc.getMessage()));
            throw rc;
        }

        // 4. PENDING → RUNNING (the process is now live). Polling to SUCCEEDED/FAILED is a follow-on
        //    endpoint (out of US-4.1 scope — see the plan) that calls runner.inspect(runnerRef)
        //    and applies the terminal transition on the row.
        analysisRepository.save(saved.markRunning(runnerRef));
        return analysisId;
    }

    /**
     * Applies the next observed transition to a started row (called by the polling endpoint,
     * which is out of scope for US-4.1 but required for the DB to ever reach SUCCEEDED/FAILED).
     * Kept here as a stable seam so a follow-on endpoint is a one-liner.
     */
    @Transactional
    public ScoreAnalysis poll(Long analysisId) {
        ScoreAnalysis current = analysisRepository.findById(analysisId).orElseThrow(
                () -> new IllegalStateException("Analiza " + analysisId + " nie znaleziona."));
        if (current.isTerminal()) {
            return current;                // idempotent — terminal rows never move
        }
        AiAnalysisRunner.PhaseStatus obs = runner.inspect(current.getRunnerRef());
        switch (obs.phase()) {
            case SUCCEEDED:
                Path root = obs.artefactRootOrNull();
                if (root == null) throw new IllegalStateException("SUCCEEDED bez artefaktów");
                return analysisRepository.save(current.succeed(
                        layout.arrangementJsonPath(root).toString(),
                        layout.arrangementMusicxmlPath(root).toString(),
                        layout.arrangementMidPath(root).toString(),
                        layout.validationTxtPath(root).toString()));
            case FAILED:
                String msg = obs.errorMessageIfFailed();
                return analysisRepository.save(current.fail(
                        msg == null || msg.isBlank() ? "Analiza AI zakończona błędem (szczegóły niedostępne)." : msg));
            case RUNNING:
            case PENDING:
            default:
                return current;            // no transition — row stays where it is
        }
    }
}
