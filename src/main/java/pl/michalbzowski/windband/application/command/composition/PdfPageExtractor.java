package pl.michalbzowski.windband.application.command.composition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PageExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * US-7.11 — physically cuts a page range out of an in-memory PDF and returns a NEW
 * valid PDF document containing exactly that range (pages stay in source order).
 *
 * <p>Backs the public voice link: a musician who was granted "strony 23–24" must receive
 * a two-page PDF, not the whole score. Built on PDFBox's own {@link PageExtractor}
 * (same 3.x dependency as {@link PdfPageCounter}, US-2.2) rather than hand-reparenting
 * pages between documents — the extractor keeps resources/fonts in sync for us.</p>
 *
 * <p>Contract mirrors {@link PdfPageCounter}'s defensive style: null/empty input, unreadable
 * PDF, an out-of-range request or any extraction error all yield {@code null} — never a throw.
 * The caller decides how to surface "could not split".</p>
 *
 * <p>Application layer: no {@code org.springframework.web} imports (ArchUnit rule).</p>
 */
@Component
public class PdfPageExtractor {

    /**
     * @param pdfBytes whole source PDF
     * @param from     1-based first page of the part (inclusive)
     * @param to       1-based last page of the part (inclusive, must be {@code >= from})
     * @return a standalone PDF holding only pages {@code from..to}, or {@code null} when the
     *         input is null/empty, the range is invalid for this document, or PDFBox rejects it.
     */
    public byte[] extractPages(byte[] pdfBytes, int from, int to) {
        if (pdfBytes == null || pdfBytes.length == 0 || from < 1 || to < from) {
            return null;
        }
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int total = doc.getNumberOfPages();
            if (total <= 0 || to > total) {
                return null;   // range does not fit the actual document — caller fails closed
            }
            // PDFBox 3.x PageExtractor is 1-based and INCLUSIVE on both ends (verified against
            // 3.0.7: extract(2,3) of a 5-page doc returns exactly pages 2–3) — pass through as-is.
            PageExtractor extractor = new PageExtractor(doc, from, to);
            try (PDDocument cut = extractor.extract();
                 ByteArrayOutputStream mem = new ByteArrayOutputStream()) {
                if (cut == null || cut.getNumberOfPages() != (to - from + 1)) {
                    return null;   // sanity: never serve a slice whose size disagrees
                }
                cut.save(mem);
                return mem.size() > 0 ? mem.toByteArray() : null;
            }
        } catch (IOException | RuntimeException ex) {
            // Loader.loadPDF throws IOException; PageExtractor.save can too; any runtime PDFBox
            // defect (bad structure) is a RuntimeException — exactly like PdfPageCounter's
            // "could not determine" contract. Listed precisely (no bare Exception) so SpotBugs
            // REC_CATCH_EXCEPTION stays honest.
            return null;
        }
    }
}
