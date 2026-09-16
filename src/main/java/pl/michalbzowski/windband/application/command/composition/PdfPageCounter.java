package pl.michalbzowski.windband.application.command.composition;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * Extracts the page count of a PDF document from in-memory bytes.
 *
 * <p>US-2.2 (Biblioteka utworów): invoked by {@link ScoreFileCommandService#upload}
 * on every {@code application/pdf} upload. The contract is "graceful on failure" —
 * any exception (password-protected file, truncated stream, empty content)
 * results in {@code null}, so callers can store a NULL page count and surface the
 * real issue to the UI instead of failing the whole upload.
 *
 * <p>This lives in the application layer (no Spring Web types — kept clean by the
 * ArchitectureTest rule against {@code org.springframework.web}).</p>
 */
@Component
public class PdfPageCounter {

    /** Null-safe: null input, empty input, unreadable PDF → returns null. */
    public Integer extract(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            return pages > 0 ? pages : null;
        } catch (Exception ex) {
            // PDF load or page enumeration failed — treat as "could not determine".
            // Logging here is intentionally absent: the caller records the outcome.
            return null;
        }
    }
}
