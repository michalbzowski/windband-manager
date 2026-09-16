package pl.michalbzowski.windband.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code windband.scores.**} properties. The upload pipeline
 * (US-2.1) reads the root path and per-file-type size caps from here so that
 * tuning does not require code changes on Railway.
 */
@ConfigurationProperties(prefix = "windband.scores")
public record ScoresConfig(
        String rootPath,
        Long maxFileSizeBytes,           // nullable so a missing property defaults cleanly
        Long maxZipFileSizeBytes) {

    public static final long DEFAULT_MAX_FILE_BYTES = 50L * 1024 * 1024;
    public static final long DEFAULT_MAX_ZIP_BYTES  = 200L * 1024 * 1024;

    /** Effective per-file cap (non-ZIP): falls back to {@link #DEFAULT_MAX_FILE_BYTES}. */
    public long maxFileSizeBytesOr() { return maxFileSizeBytes == null ? DEFAULT_MAX_FILE_BYTES : maxFileSizeBytes; }

    /** Effective ZIP cap: falls back to {@link #DEFAULT_MAX_ZIP_BYTES}. */
    public long maxZipFileSizeBytesOr() { return maxZipFileSizeBytes == null ? DEFAULT_MAX_ZIP_BYTES : maxZipFileSizeBytes; }
}
