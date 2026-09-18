package pl.michalbzowski.windband.application.command.scoreanalysis;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * US-4.1 — owns the on-disk layout of AI-analysis artefacts:
 *
 * <pre>
 *   {outputRoot}/{analysisId}/
 *       arrangement.json
 *       arrangement.musicxml
 *       arrangement.mid
 *       validation.txt
 *   </pre>
 *
 * The root is configurable via {@code app.scoreanalysis.output-root} (defaults to
 * {@code ./data/scoreanalysis/out}) and every analysis id maps to a deterministic
 * sub-folder so a follow-on cleanup job can simply list the root.
 */
public class AiArtifactsLayout {

    private final Path outputRoot;

    public AiArtifactsLayout(Path outputRoot) {
        // The Bean factory (ScoreAnalysisConfiguration) guarantees non-null + non-blank; a null
        // passed here is a programming bug that surfaces as an NPE on the FIRST use, which is
        // what we want — NOT a partially-initialized AiArtifactsLayout instance in the registry.
        this.outputRoot = outputRoot == null ? Paths.get("./data/scoreanalysis/out") : outputRoot;
    }

    /** The artefact root directory for a specific analysis id. */
    public Path outputFor(long analysisId) {
        return outputRoot.resolve(Long.toString(analysisId));
    }

    public Path arrangementJsonPath(Path root)     { return root.resolve("arrangement.json"); }
    public Path arrangementMusicxmlPath(Path root) { return root.resolve("arrangement.musicxml"); }
    public Path arrangementMidPath(Path root)      { return root.resolve("arrangement.mid"); }
    public Path validationTxtPath(Path root)       { return root.resolve("validation.txt"); }
}
