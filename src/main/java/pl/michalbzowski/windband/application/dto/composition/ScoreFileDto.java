package pl.michalbzowski.windband.application.dto.composition;

import java.time.Instant;

/**
 * Response payload of {@code POST /bands/{bandId}/compositions/{id}/files} and
 * the rows listed by the ZIP-expand endpoint (US-2.3). The {@code parentFileId}
 * field is null for standalone uploads; non-null when this row was extracted
 * from a ZIP archive, linking back to the ZIP's own row.
 */
public record ScoreFileDto(
        Long fileId,
        Long compositionId,
        String originalName,
        long sizeBytes,
        String mimeType,
        Integer pageCount,
        boolean isZip,
        String sha256,
        Long parentFileId,
        Instant uploadedAt) {
}
