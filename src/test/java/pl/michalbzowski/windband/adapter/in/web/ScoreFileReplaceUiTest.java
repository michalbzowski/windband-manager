package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.15 — "Wymień plik" on the composition detail page, driven through the real browser:
 * a READY ("Gotowy") piece with one verified voice bound to an uploaded 2-page PDF gets its
 * file swapped in place. Afterwards the page must show: the SAME row with the new name and
 * page count (+ "wymieniono" stamp), the voice mapping untouched in the parts table, and the
 * status badge back to "Szkic" — while the DB proves the mapping's FK still points at the
 * original score_files id and only the stale verification fell.
 *
 * <p>The hidden {@code #score-replace-input} is fed via DataTransfer + a dispatched change
 * event (the same technique {@code ScoreFileUploadUiTest} uses for the upload input) because
 * the native picker cannot be scripted in headless mode.</p>
 */
class ScoreFileReplaceUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private String title;
    private Long compositionId;
    private Long fileId;

    @BeforeEach
    void seedReadyCompositionWithVerifiedPart() throws Exception {
        title = "Replace UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'wymiana pliku', null, null, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        Path scoresRoot = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), "windband-ui-scores"));
        Path pdfPath = scoresRoot.resolve(UUID.randomUUID() + "_Polonez_v1.pdf");
        byte[] pdf = buildPdf(2);
        Files.write(pdfPath, pdf);

        jdbcTemplate.update("""
                INSERT INTO score_files
                    (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at)
                VALUES (%s, 'application/pdf', %d,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        '%s', 'Polonez v1.pdf', 2, CURRENT_TIMESTAMP)
                """.formatted(compositionId, pdf.length, pdfPath.toString()));
        fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ? AND original_name = 'Polonez v1.pdf'",
                Long.class, compositionId);

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO composition_instruments (composition_id, instrument_id, instrument_role," +
                        " page_from, page_to, score_file_id, source, confidence_score," +
                        " verified_by, verified_at, created_at, updated_at) VALUES " +
                        "(?, ?, 'Trąbka 1', 1, 2, ?, 'MANUAL', 1.0, 'librarian@test', CURRENT_TIMESTAMP," +
                        " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, instrumentId, fileId);
    }

    @Test
    void replaceFile_keepsMapping_dropsToDraft_stampsTheSwap() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        FluentWait<org.openqa.selenium.WebDriver> wait =
                new WebDriverWait(driver, WAIT).pollingEvery(Duration.ofMillis(150));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));

        // Pre-conditions on the rendered page: READY badge + a replace button on the file row.
        assertThat(driver.findElement(By.cssSelector(".composition-title-row .badge")).getText())
                .as("seeded composition renders as Gotowy").containsIgnoringCase("Gotowy");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".file-replace-btn")));

        byte[] newPdf = buildPdf(3);
        String b64 = java.util.Base64.getEncoder().encodeToString(newPdf);
        ((JavascriptExecutor) driver).executeScript(
                "var btn = document.querySelector('.file-replace-btn');" +
                "var input = document.getElementById('score-replace-input');" +
                "input.setAttribute('data-url', btn.getAttribute('data-url'));" +
                "input.setAttribute('data-file-name', btn.getAttribute('data-file-name'));" +
                "var binaryStr = atob(arguments[0]);" +
                "var bytes = new Uint8Array(binaryStr.length);" +
                "for (var i = 0; i < binaryStr.length; i++) { bytes[i] = binaryStr.charCodeAt(i); }" +
                "var file = new File([bytes], 'Polonez v2.pdf', { type: 'application/pdf' });" +
                "var dt = new DataTransfer(); dt.items.add(file);" +
                "input.files = dt.files;" +
                "input.dispatchEvent(new Event('change', { bubbles: true }));",
                b64);

        // The page reloads itself after the swap — wait until the server-rendered truth is back.
        wait.until(d -> "Szkic".equalsIgnoreCase(
                d.findElement(By.cssSelector(".composition-title-row .badge")).getText().trim()));

        assertThat(driver.getPageSource())
                .as("row shows the new file with its new page count and the swap stamp")
                .contains("Polonez v2.pdf").contains("3 stron").contains("wymieniono");
        assertThat(driver.getPageSource())
                .as("the voice mapping survived the swap: role + file name render in the parts table")
                .contains("Trąbka 1");

        // DB: same row id, stale audit cleared, mapping FK intact, READY → DRAFT.
        Map<String, Object> file = jdbcTemplate.queryForMap(
                "SELECT * FROM score_files WHERE id = ?", fileId);
        assertThat(file.get("PAGE_COUNT")).isEqualTo(3);
        assertThat(file.get("STORAGE_PATH").toString()).endsWith("Polonez v2.pdf");
        assertThat(file.get("REPLACED_AT")).isNotNull();

        Map<String, Object> part = jdbcTemplate.queryForMap(
                "SELECT * FROM composition_instruments WHERE composition_id = ?", compositionId);
        assertThat(((Number) part.get("SCORE_FILE_ID")).longValue())
                .as("the part still points at the SAME score file row").isEqualTo(fileId);
        assertThat(part.get("PAGE_TO")).isEqualTo(2);
        assertThat(part.get("VERIFIED_AT")).as("stale audit pair cleared").isNull();
        assertThat(part.get("VERIFIED_BY")).isNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM compositions WHERE id = ?", String.class, compositionId))
                .isEqualTo("DRAFT");
    }

    private static byte[] buildPdf(int pages) {
        try (PDDocument doc = new PDDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (var cs = new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
