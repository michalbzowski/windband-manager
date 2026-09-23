package pl.michalbzowski.windband.application.command.composition;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.11 — the physical PDF split. Every assertion round-trips the extracted bytes
 * through PDFBox and counts pages for real; a link that "only decorates the filename"
 * (the old US-7.10 behaviour) would return the original byte array and fail the count.
 */
class PdfPageExtractorTest {

    private final PdfPageExtractor extractor = new PdfPageExtractor();

    private static int pageCount(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    void extractPages_middleRange_returnsExactlyThatMany() throws Exception {
        byte[] five = TestPdfBuilder.generate(5);
        byte[] cut = extractor.extractPages(five, 2, 3);
        assertThat(cut).isNotNull();
        assertThat(pageCount(cut)).isEqualTo(2);
        // The whole source is 5 pages — the result must be a smaller, independent document.
        assertThat(cut.length).isLessThan(five.length);
    }

    @Test
    void extractPages_singlePage_returnsOnePage() throws Exception {
        byte[] cut = extractor.extractPages(TestPdfBuilder.generate(4), 3, 3);
        assertThat(cut).isNotNull();
        assertThat(pageCount(cut)).isEqualTo(1);
    }

    @Test
    void extractPages_wholeDocumentRoundTrips() throws Exception {
        byte[] src = TestPdfBuilder.generate(3);
        byte[] cut = extractor.extractPages(src, 1, 3);
        assertThat(cut).isNotNull();
        assertThat(pageCount(cut)).isEqualTo(3);
    }

    @Test
    void extractPages_rangeBeyondPageCount_returnsNull() {
        assertThat(extractor.extractPages(TestPdfBuilder.generate(2), 1, 5)).isNull();
    }

    @Test
    void extractPages_invertedRange_returnsNull() {
        assertThat(extractor.extractPages(TestPdfBuilder.generate(3), 4, 2)).isNull();
    }

    @Test
    void extractPages_nullOrEmptyInput_returnsNull() {
        assertThat(extractor.extractPages(null, 1, 2)).isNull();
        assertThat(extractor.extractPages(new byte[0], 1, 2)).isNull();
    }

    @Test
    void extractPages_garbageBytes_returnsNull() {
        assertThat(extractor.extractPages(new byte[] { 1, 2, 3, 4, 5 }, 1, 1)).isNull();
    }
}
