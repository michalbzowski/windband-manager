package pl.michalbzowski.windband.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * US-4.1 / Score Analyzer integration — bound from {@code app.scoreanalysis.**}.
 *
 * <p>Besides the artefact output root this carries the connection settings of the external
 * Score Analyzer service (Python/FastAPI, see the {@code score-analyzer} repo):
 * <ul>
 *   <li>{@code analyzer-base-url} — when non-blank the HTTP runner is wired; when blank the
 *       in-memory {@code StubAiAnalysisRunner} stays active (tests / dev without the service).</li>
 *   <li>{@code analyzer-shared-token} — optional service key sent as {@code X-Analyzer-Key}
 *       (the analyzer enforces it when its own {@code ANALYZER_SHARED_TOKEN} env is set).</li>
 *   <li>{@code analyzer-timeout-seconds} — request timeout for the upload call.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.scoreanalysis")
public record ScoreAnalysisConfig(
        String outputRoot,
        String analyzerBaseUrl,
        String analyzerSharedToken,
        Integer analyzerTimeoutSeconds) {

    public static final String DEFAULT_OUTPUT_ROOT = "./data/scoreanalysis/out";
    public static final int DEFAULT_ANALYZER_TIMEOUT_SECONDS = 120;

    public ScoreAnalysisConfig {
        // Tolerate a blank (env placeholder resolved to "") as "not configured"; trim the rest.
        analyzerBaseUrl = normalize(analyzerBaseUrl);
        analyzerSharedToken = normalize(analyzerSharedToken);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Effective root — falls back to the default if not set. */
    public String outputRootOr() { return outputRoot == null ? DEFAULT_OUTPUT_ROOT : outputRoot; }

    /** True when an external Score Analyzer service is configured. */
    public boolean hasAnalyzer() { return analyzerBaseUrl != null; }

    /** Effective analyzer request timeout. */
    public int analyzerTimeoutSecondsOr() {
        return analyzerTimeoutSeconds == null || analyzerTimeoutSeconds <= 0
                ? DEFAULT_ANALYZER_TIMEOUT_SECONDS : analyzerTimeoutSeconds;
    }
}
