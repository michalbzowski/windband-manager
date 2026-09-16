package pl.michalbzowski.windband.application.command.composition;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/**
 * Test helper: builds N-page valid PDFs at test time so the repo doesn't have
 * to grow binary fixtures. Each page is an empty LETTER-size rect — enough for
 * {@code PDDocument.getNumberOfPages()} to return a deterministic count.</p>
 */
final class TestPdfBuilder {

    private TestPdfBuilder() { }

    /** @return in-memory PDF with exactly {@code pages} valid pages (>= 1). */
    static byte[] generate(int pages) {
        if (pages < 1) {
            throw new IllegalArgumentException("need at least 1 page");
        }
        try (PDDocument doc = new PDDocument();
             java.io.ByteArrayOutputStream mem = new java.io.ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    // One empty text block so the page has a non-nil content stream.
                    cs.beginText();
                    cs.endText();
                }
            }
            doc.save(mem);
            return mem.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to build test PDF", e);
        }
    }
}
