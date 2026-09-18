package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * US-4.1 — default in-memory {@link AiAnalysisRunner}. Honours the real pipeline's two-step
 * API: {@code start} returns a stable {@code runnerRef}; {@code inspect(ref)} reports the phase.
 * Because this stub is single-shot it transitions to SUCCEEDED (or FAILED when the input
 * file name contains "failme") on the first {@code inspect} call after {@code start}.
 * The artefacts (arrangement.json, .musicxml, .mid) are created at {@code inspect} time so
 * the FS-path assertions in the integration test see real files.
 */
@Component
public class StubAiAnalysisRunner implements AiAnalysisRunner {

    private static final AtomicInteger SEQ = new AtomicInteger();
    // ref -> input path; lets inspect know where to read + force-FAIL on name match.
    private final AtomicReference<java.util.Map<String, Path>> recentRequests =
            new AtomicReference<>(new java.util.concurrent.ConcurrentHashMap<>());

    @Override
    public String start(AnalysisRequest request) {
        // The contract already validated inputPath; we just remember it for inspect().
        String ref = "stub-" + SEQ.incrementAndGet();
        recentRequests.get().put(ref, request.inputPath());
        return ref;
    }

    @Override
    public PhaseStatus inspect(String runnerRef) {
        if (runnerRef == null || !runnerRef.startsWith("stub-")) {
            return PhaseStatus.failed("unknown runnerRef: " + runnerRef);
        }
        Path input = recentRequests.get().get(runnerRef);
        if (input == null) {
            return PhaseStatus.failed("no record for " + runnerRef);
        }
        String fileName = String.valueOf(input.getFileName());
        boolean failme = fileName.contains("failme");
        Path outDir = resolveOutDir(runnerRef);           // deterministic on-disk location per stub ref
        if (failme) {
            // Fail fast: no artefacts written; the caller (command service) maps FAILED.
            return PhaseStatus.failed("Symulowany błąd pipeline AI (stub runner — failme trigger)");
        }
        try {
            Files.createDirectories(outDir);
            Files.write(outDir.resolve("arrangement.json"), "[]".getBytes(StandardCharsets.UTF_8));
            Files.write(outDir.resolve("arrangement.musicxml"), "<score/>".getBytes(StandardCharsets.UTF_8));
            Files.write(outDir.resolve("arrangement.mid"), new byte[]{0x4D, 0x54, 0x68, 0x64});
        } catch (IOException e) {
            return PhaseStatus.failed("stub could not write artefacts: " + e.getMessage());
        }
        return PhaseStatus.succeeded(outDir);
    }

    /** Stable on-disk location for a stub ref — tests discover this via the DTO. */
    private static Path resolveOutDir(String runnerRef) {
        // The command service is expected to pass the *analysis id* via the layout; here we
        // just derive a deterministic path next to the stub state file so inspect(ref) works
        // from any working directory (tests set it through a property if they need).
        Path base = Path.of(System.getProperty("stub.out.dir", "./data/scoreanalysis/stub-out"));
        return base.resolve(runnerRef);
    }
}
