package pl.michalbzowski.windband.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * US-4.1 — bound from {@code app.scoreanalysis.**}. Only the output root is
 * exposed in this MVP scope; a follow-on story may add worker timeout, max
 * concurrent runs, etc.
 */
@ConfigurationProperties(prefix = "app.scoreanalysis")
public record ScoreAnalysisConfig(
        String outputRoot) {

    public static final String DEFAULT_OUTPUT_ROOT = "./data/scoreanalysis/out";

    /** Effective root — falls back to the default if not set. */
    public String outputRootOr() { return outputRoot == null ? DEFAULT_OUTPUT_ROOT : outputRoot; }
}
