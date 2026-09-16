package pl.michalbzowski.windband.application.command.composition;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.application.config.ScoresConfig;

/**
 * Input validation for the upload pipeline (US-2.1): MIME whitelist, per-type
 * size caps, and ZIP-slip detection on any archive being stored. The validator
 * is I/O-light — it inspects only the declared {@code MultipartFile} metadata
 * (or an in-memory ZIP scan) so it can run before a temporary file is created.
 */
@Component
public class UploadValidator {

    /** Allowed upload MIME types for score files. Keep in sync with UI copy. */
    static final Set<String> MIME_WHITELIST = Set.of(
            "application/pdf",
            "application/zip",
            "image/jpeg",
            "image/png");

    private final ScoresConfig config;

    public UploadValidator(ScoresConfig config) {
        this.config = config;
    }

    /** Throws {@link UploadRejectedException} (415) if the declared MIME type is outside the whitelist. */
    public void requireAllowedMime(String contentType) {
        if (contentType == null || !MIME_WHITELIST.contains(contentType)) {
            throw new UploadRejectedException(415,
                    "Nieobsługiwany typ pliku. Dozwolone: PDF, ZIP, JPEG, PNG.");
        }
    }

    /** Throws {@link UploadRejectedException} (413) if the size exceeds the type-specific cap. */
    public void requireAllowedSize(long contentLength, String contentType) {
        long cap = capFor(contentType);
        if (contentLength > cap) {
            throw new UploadRejectedException(413,
                    "Plik przekracza dozwolony rozmiar (" + (cap / 1024 / 1024) + " MB).");
        }
    }

    /**
     * Throws {@link UploadRejectedException} (413) if a single ZIP entry exceeds the
     * non-ZIP per-file cap. Re-applied at expansion time (US-2.3) because the archive-level
     * ZIP cap checked at upload does not bound the size of an individual extracted entry —
     * one 190 MB entry inside a 200 MB ZIP would pass upload but spike RAM on extraction.
     */
    public void requireAllowedEntrySize(long contentLength) {
        if (contentLength > config.maxFileSizeBytesOr()) {
            throw new UploadRejectedException(413,
                    "Pozycja archiwum przekracza dozwolony rozmiar pojedynczego pliku ("
                            + (config.maxFileSizeBytesOr() / 1024 / 1024) + " MB).");
        }
    }

    /** Throws {@link UploadRejectedException} (422) if any ZIP entry path escapes the target directory. */
    public void requireZipContentSafe(byte[] zipBytes) {
        List<String> unsafe = unsafeEntries(zipBytes);
        if (!unsafe.isEmpty()) {
            throw new UploadRejectedException(422, "Niebezpieczna zawartość archiwum ZIP: " + unsafe);
        }
    }

    // ---- internals ---------------------------------------------------------

    private long capFor(String contentType) {
        if (config == null) {
            return "application/zip".equals(contentType)
                    ? ScoresConfig.DEFAULT_MAX_ZIP_BYTES
                    : ScoresConfig.DEFAULT_MAX_FILE_BYTES;
        }
        return "application/zip".equals(contentType)
                ? config.maxZipFileSizeBytesOr()
                : config.maxFileSizeBytesOr();
    }

    /** Scan the ZIP in-memory and list every entry whose normalized path tries to escape the target directory. */
    static List<String> unsafeEntries(byte[] zipBytes) {
        var out = new java.util.ArrayList<String>();
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                if (isUnsafe(entry.getName())) {
                    out.add(entry.getName());
                }
                entry = zis.getNextEntry();
            }
        } catch (java.io.IOException e) {
            // A corrupt archive is not a ZIP-slip; the storage adapter reports it on write.
        }
        return List.copyOf(out);
    }

    private static boolean isUnsafe(String raw) {
        if (raw == null || raw.isBlank()) return false;
        var p = java.nio.file.Path.of(raw).normalize();
        if (p.isAbsolute() || p.startsWith(java.nio.file.Path.of(".."))) {
            return true;
        }
        // Also reject drive letters and backslash separators (Windows-style escaping).
        return raw.contains("\\") || raw.matches("^[A-Za-z]:.*");
    }

    /**
     * Exception the upload pipeline throws for every client-side rejection.
     * Carries an HTTP status so the global exception handler can map it 1:1.
     */
    public static class UploadRejectedException extends RuntimeException {
        private final int httpStatus;

        public UploadRejectedException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() { return httpStatus; }
    }
}
