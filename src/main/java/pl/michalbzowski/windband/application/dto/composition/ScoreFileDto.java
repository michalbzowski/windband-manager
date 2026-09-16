package pl.michalbzowski.windband.application.dto.composition;

import java.time.Instant;

/**
 * Response payload of {@code POST /bands/{bandId}/compositions/{id}/files}: the
 * uploaded score-file's identity + storage metadata. Downstream steps (Epic 2
 * US-2.2..US-2.4) branch on {@code fileId}.
 */
public record ScoreFileDto(
        Long fileId,
        Long compositionId,
        String originalName,
        long sizeBytes,
        String mimeType,
        Integer pageCount,       // null when unknown (ZIP, or PDF pre-analysis)
        boolean isZip,
        String sha256,
        Instant uploadedAt) {
}
