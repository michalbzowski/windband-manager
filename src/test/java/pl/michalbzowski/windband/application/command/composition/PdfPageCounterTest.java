package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PdfPageCounter}. Uses {@link TestPdfBuilder} to
 * generate real PDFs on the fly so we don't ship a fixture.</p>
 */
class PdfPageCounterTest {

    private final PdfPageCounter counter = new PdfPageCounter();

    @Test
    @DisplayName("returns 1 for a one-page PDF")
    void one_page_pdf() {
        assertThat(counter.extract(TestPdfBuilder.generate(1))).isEqualTo(1);
    }

    @Test
    @DisplayName("returns 5 for a five-page PDF (deterministic, not approximate)")
    void multi_page_pdf() {
        assertThat(counter.extract(TestPdfBuilder.generate(5))).isEqualTo(5);
    }

    @Test
    @DisplayName("returns null on null input (graceful)")
    void null_input_returns_null() {
        assertThat(counter.extract(null)).isNull();
    }

    @Test
    @DisplayName("returns null on empty input (graceful)")
    void empty_input_returns_null() {
        assertThat(counter.extract(new byte[0])).isNull();
    }

    @Test
    @DisplayName("returns null on non-PDF bytes (graceful, no exception)")
    void non_pdf_bytes_return_null() {
        byte[] garbage = "not really a pdf at all".getBytes();
        Integer result = counter.extract(garbage);   // must not throw
        assertThat(result).isNull();
    }
}
