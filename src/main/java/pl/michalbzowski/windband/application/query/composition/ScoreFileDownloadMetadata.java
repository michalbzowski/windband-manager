package pl.michalbzowski.windband.application.query.composition;

/**
 * Scalar metadata for one downloadable score file (US-2.4). Carries only what the adapter
 * needs to produce an HTTP response — never a JPA entity, so the application layer stays
 * Spring-Web-free and the controller stays free of detached lazy-assoc traps.
 */
public record ScoreFileDownloadMetadata(
        Long fileId,
        String originalName,
        String mimeType,
        long sizeBytes,
        boolean isZip) {}
