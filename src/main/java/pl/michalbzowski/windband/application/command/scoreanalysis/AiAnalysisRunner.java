package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.nio.file.Path;

/**
 * SPI for the AI analysis pipeline (US-4.1).
 *
 * <p>This lives in the application layer — it does NOT reference any Spring Web type —
 * so the ArchUnit rule is satisfied. The adapter (REST controller) maps the HTTP request
 * to {@link AnalysisRequest}; the runner either delegates to a subprocess
 * ({@code ProcessBuilderAiAnalysisRunner}) or fakes one in tests (Stub runner).
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #start(AnalysisRequest)} returns immediately (non-blocking) once the
 *       pipeline has been spawned. Returns a non-empty {@code runnerRef} that can be passed
 *       to {@link #inspect(String)}.</li>
 *   <li>{@link #inspect(String)} reports the current phase. It may be called repeatedly by
 *       the UI polling endpoint; implementations MUST be idempotent and thread-safe for a
 *       single runnerRef (a given ref is polled from a single JVM, never from multiple).</li>
 *   <li>Phase transitions are owned by the caller ({@code ScoreAnalysisCommandService});
 *       the runner only reports what it observes — it does NOT mutate the domain entity.</li>
 * </ul>
 */
public interface AiAnalysisRunner {

    /**
     * Spawns the windband-ai pipeline on {@link AnalysisRequest#inputPath()} and writes
     * artefacts to {@link AnalysisRequest#outputDir()}.
     *
     * @return a non-blank identifier (e.g. PID as decimal string) usable with {@link #inspect(String)}
     * @throws RunnerNotConfiguredException if the pipeline binary is missing or not yet installed
     */
    String start(AnalysisRequest request);

    /**
     * Inspects the current phase of the runner identified by {@code runnerRef}.
     * The call MUST be non-blocking (no wait()) — the caller decides when to poll again.
     *
     * @throws IllegalArgumentException if the ref is unknown or invalid
     */
    PhaseStatus inspect(String runnerRef);

    /** Everything the runner needs to launch a pipeline run on one input file. */
    record AnalysisRequest(Path inputPath, Path outputDir) {
        public AnalysisRequest {
            if (inputPath == null || !java.nio.file.Files.isRegularFile(inputPath)) {
                throw new IllegalArgumentException("inputPath must be an existing regular file");
            }
            if (outputDir == null) {
                throw new IllegalArgumentException("outputDir required");
            }
        }
    }

    /** Current phase observation + any artefact root discovered so far. */
    record PhaseStatus(Phase phase, Path artefactRootOrNull, String errorMessageIfFailed) {
        public enum Phase { PENDING, RUNNING, SUCCEEDED, FAILED }

        public static PhaseStatus running() { return new PhaseStatus(Phase.RUNNING, null, null); }
        public static PhaseStatus succeeded(Path root) { return new PhaseStatus(Phase.SUCCEEDED, root, null); }
        public static PhaseStatus failed(String message) { return new PhaseStatus(Phase.FAILED, null, message); }
    }

    /** Raised when the pipeline binary is missing or misconfigured. Maps to HTTP 501. */
    class RunnerNotConfiguredException extends RuntimeException {
        public RunnerNotConfiguredException(String message) { super(message); }
    }
}
