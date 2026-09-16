package pl.michalbzowski.windband.application.command.composition;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.dto.composition.ZipEntryDto;

/**
 * Extracts individual entries from a ZIP archive in-memory (US-2.3).
 *
 * <p>Each file entry (not directories) is read into byte memory and converted
 * to a {@link ZipEntryExtracted} record. Directory entries are silently skipped —
 * they have no content and cannot be mapped to instruments.</p>
 *
 * <p>MIME type is inferred from the file extension. This heuristic is sufficient
 * for the score-files use case (PDF parts, PNG/JPEG images); it avoids pulling in
 * a MIME-sniffing library into the application layer.</p>
 */
@Component
public class ZipEntryExtractor {

    /**
     * Result of one successful ZIP extraction: one list of entries, each carrying
     * its content bytes and metadata. The caller is responsible for persisting
     * each entry as a separate {@code score_files} row.
     */
    public record ExtractionResult(
            List<ZipEntryDto> dtos,
            List<ZipEntryBytes> bytes) {

        /** Raw bytes + metadata for one entry, ready for disk write. */
        public record ZipEntryBytes(byte[] content, String entryName, long sizeBytes) {}
    }

    /**
     * Extract all file entries from {@code zipBytes}.
     *
     * @throws UploadValidator.UploadRejectedException (422) if any entry path is unsafe
     *         (ZIP-slip) — reuses the existing validator logic.
     */
    public ExtractionResult extract(byte[] zipBytes, UploadValidator validator) {
        // Re-use the ZIP-slip check so a malicious archive cannot write outside its root.
        validator.requireZipContentSafe(zipBytes);

        List<ZipEntryDto> dtos = new ArrayList<>();
        List<ExtractionResult.ZipEntryBytes> rawEntries = new ArrayList<>();

        try (var zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    byte[] content = zis.readAllBytes();
                    String name = entry.getName();
                    String mime = inferMime(name);

                    dtos.add(new ZipEntryDto(name, mime, content.length, "application/pdf".equals(mime)));
                    rawEntries.add(new ExtractionResult.ZipEntryBytes(content, name, content.length));
                }
                entry = zis.getNextEntry();
            }
        } catch (java.io.IOException e) {
            // Corrupt or truncated ZIP — surface as a clear I/O error for the handler.
            throw new java.io.UncheckedIOException("Nie udało się odczytać zawartości archiwum ZIP.", e);
        }

        return new ExtractionResult(List.copyOf(dtos), List.copyOf(rawEntries));
    }

    private static String inferMime(String entryName) {
        if (entryName == null || entryName.isBlank()) return "application/octet-stream";
        String lower = entryName.toLowerCase();
        if (lower.endsWith(".pdf"))  return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
