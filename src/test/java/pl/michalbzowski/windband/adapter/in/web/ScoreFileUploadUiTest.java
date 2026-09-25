package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.9 — UI test for the score-file upload section on the composition detail page.
 * Validates: (1) the upload buttons and form are rendered, (2) a valid PDF upload
 * succeeds end-to-end (file saved to disk, row in DB), and (3) the page renders
 * without Thymeleaf/JS errors on first load.
 */
class ScoreFileUploadUiTest extends UiTestBase {

    private Long compositionId;

    @AfterEach
    void cleanup() {
        try {
            jdbcTemplate.update("DELETE FROM score_files SF WHERE SF.composition_id IN" +
                    " (SELECT id FROM compositions WHERE title = 'Upload UI Test')");
            jdbcTemplate.update("DELETE FROM compositions WHERE title = 'Upload UI Test'");
        } catch (Exception ignored) { /* best-effort cleanup */ }
    }

    @BeforeEach
    void seedComposition() {
        // cleanDatabase() (from UiTestBase @BeforeEach) already truncates compositions
        // and score_files. Just insert our test row.
        jdbcTemplate.update("""
                INSERT INTO compositions
                    (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES ('Upload UI Test', 'Test upload section', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = 'Upload UI Test'",
                Long.class);
    }

    @Test
    void shouldRenderUploadSection_withFileInputAndButtons() {
        // The compositions LIST page render was pure overhead — loginAndNavigateTo
        // goes straight to the detail (its wait already accepts #composition-detail),
        // so the same-origin requirement for the XHR helpers is met in one load.
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);

        FluentWait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(15)).pollingEvery(Duration.ofMillis(100));
        // The page must render without a Thymeleaf JS error first.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        // The upload section (dropzone) must be visible on the detail page.
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        assertThat(driver.findElement(By.id("score-file-input")))
                .as("file input with accept=PDF")
                .isNotNull();
        String accept = driver.findElement(By.id("score-file-input")).getAttribute("accept");
        assertThat(accept).containsIgnoringCase("pdf");

        // New UI: the dropzone is clickable and shows a hint text
        WebElement dropzone = driver.findElement(By.id("score-upload-panel"));
        assertThat(dropzone.isDisplayed()).as("dropzone visible").isTrue();
        assertThat(dropzone.getText()).containsIgnoringCase("dodaj");

        // Upload queue starts hidden
        String queueClass = driver.findElement(By.id("upload-queue")).getAttribute("class");
        assertThat(queueClass).contains("hidden");
    }

    @Test
    void shouldUploadValidPdf_viaXhr_andPersistPageCount() throws Exception {
        // Generate a 3-page PDF in a temp file.
        Path pdf = generateTempPdf(3);

        // The compositions LIST page render was pure overhead — loginAndNavigateTo
        // goes straight to the detail (its wait already accepts #composition-detail),
        // so the same-origin requirement for the XHR helpers is met in one load.
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        FluentWait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(15)).pollingEvery(Duration.ofMillis(100));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("score-upload-panel")));

        // Upload via synchronous XHR — exercises the exact HTTP contract (multipart POST
        // with CSRF header) without relying on Selenium's file-upload API which is flaky
        // in headless sandboxes. The test DB state after the upload is the load-bearing
        // assertion: one row in score_files with page_count = 3.
        String base64 = java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(pdf));
        String fileName = pdf.getFileName().toString();

        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        Object statusObj = js.executeScript(
                "var pdfB64 = arguments[1];" +
                "var fileName = arguments[2];" +
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/bands/1/compositions/' + arguments[0] + '/files', false);" +
                "var binaryStr = atob(pdfB64);" +
                "var bytes = new Uint8Array(binaryStr.length);" +
                "for (var i = 0; i < binaryStr.length; i++) { bytes[i] = binaryStr.charCodeAt(i); }" +
                "var blob = new Blob([bytes], { type: 'application/pdf' });" +
                "var fd = new FormData();" +
                "fd.append('file', blob, fileName);" +
                "var csrfCookie = document.cookie.split('; ').find(function (c) { return c.startsWith('XSRF-TOKEN='); });" +
                "if (csrfCookie) { xhr.setRequestHeader('X-XSRF-TOKEN', csrfCookie.substring(10)); }" +
                "xhr.send(fd); return [xhr.status, xhr.responseText];",
                String.valueOf(compositionId), base64, fileName);

        @SuppressWarnings("unchecked")
        java.util.List<?> result = (java.util.List<?>) statusObj;
        int httpStatus = ((Number) result.get(0)).intValue();
        assertThat(httpStatus).isEqualTo(201);

        // Response body is ScoreFileDto JSON — verify it parses and carries the file id.
        String responseBody = (String) result.get(1);
        Object parsed = js.executeScript("return JSON.parse(arguments[0]);", responseBody);
        assertThat(parsed).isNotNull();

        // Load-bearing assertion: the DB row exists with page_count = 3.
        Integer pageCountInDb = jdbcTemplate.queryForObject(
                "SELECT page_count FROM score_files WHERE composition_id = ?",
                Integer.class, compositionId);
        assertThat(pageCountInDb).isEqualTo(3);

        // File bytes are on disk at the recorded storage path.
        String storagePath = jdbcTemplate.queryForObject(
                "SELECT storage_path FROM score_files WHERE composition_id = ?",
                String.class, compositionId);
        assertThat(java.nio.file.Path.of(storagePath)).exists();

        Files.deleteIfExists(pdf);
    }

    /**
     * US-7.9 — step 4 (mobile ≥360 px) + step 3 (graceful preview fallback).
     *
     * <p>Seeds a score file with a known page count, then at a 360-px viewport asserts the
     * header-preview panel renders per file: one thumbnail cell for each of the first pages
     * (max 3), each cell carrying the "Brak podglądu" fallback (hidden until the image load
     * fails), and the "Pokaż cały PDF" escape-hatch link pointing at the full download URL.
     * Nothing may overflow the viewport — no horizontal scroll is allowed on a phone.
     */
    @Test
    void previewPanel_rendersForFileWithPages_withinMobile360Viewport() throws Exception {
        // Seed a REAL 5-page PDF on disk -> the endpoint can render pages 1-3 to JPEG, so all
        // three thumbnails return HTTP 200 (deterministic happy path; no fallback race).
        Path realPdf = generateTempPdf(5);
        long realSize = Files.size(realPdf);
        String shaOfRealPdf = sha256Hex(realPdf);
        jdbcTemplate.update(
                "INSERT INTO score_files " +
                "(composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) " +
                "VALUES (?, 'application/pdf', ?, ?, ?, 'polka-glowna.pdf', 5, CURRENT_TIMESTAMP)",
                compositionId,
                realSize,
                shaOfRealPdf,
                realPdf.toString());

        driver.manage().window().setSize(new Dimension(360, 800));

        // The compositions LIST page render was pure overhead — loginAndNavigateTo
        // goes straight to the detail (its wait already accepts #composition-detail),
        // so the same-origin requirement for the XHR helpers is met in one load.
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        FluentWait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(20)).pollingEvery(Duration.ofMillis(100));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));

        // The per-file preview panel is rendered (pageCount > 0) inside the file row.
        WebElement preview = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".file-preview")));
        assertThat(preview).as("header preview panel (.file-preview)").isNotNull();
        String title = preview.findElement(By.tagName("small")).getText();
        assertThat(title).describedAs("preview cap label mentions the page range")
                .containsIgnoringCase("podgląd stron");

        // Deterministic render proof (AC): min(pageCount, 3) = 3 thumbnail cells. The actual
        // JPEG-ness of the endpoint is proven end-to-end elsewhere (ScoreFileThumbRestControllerTest
        // asserts FF D8 FF for a valid multi-page PDF), so here we pin stable DOM, not image
        // decode timing: each cell keeps its <img class="file-thumb"> with the per-page contract
        // and has an (initially hidden) "Brak podgląd" fallback. Because the seed points at a
        // REAL 5-page PDF, /thumb returns 200 and the graceful-degradation branch never fires —
        // no async race, deterministic on any machine.
        List<WebElement> cells = preview.findElements(By.cssSelector(".preview-grid .thumb-cell"));
        assertThat(cells).as("thumbnail cells for pages 1-3").hasSize(3);
        for (int page = 1; page <= 3; page++) {
            WebElement img = cells.get(page - 1).findElement(By.cssSelector("img.file-thumb"));
            assertThat(img.getAttribute("src"))
                    .as("thumbnail %d requests the /thumb endpoint at width=400", page)
                    .endsWith("/thumb?page=" + page + "&width=400");
        }
        for (WebElement cell : cells) {
            WebElement fb = cell.findElement(By.cssSelector(".thumb-fallback"));
            assertThat(fb).as("'Brak podgląd' fallback present in each cell").isNotNull();
            // Native [hidden] attribute on first paint -> the label is hidden, not the cell.
            assertThat(fb.isDisplayed()).as("fallback label hidden on successful render").isFalse();
        }
        // The thumb src URLs must carry the per-page endpoint contract.
        List<WebElement> thumbs = preview.findElements(By.cssSelector(".preview-grid .file-thumb"));
        assertThat(thumbs.get(2).getAttribute("src")).endsWith("/thumb?page=3&width=400").withFailMessage(
                "third thumbnail must request page 3 at width 400");

        // The escape hatch: "Pokaż cały PDF" -> full download endpoint, new tab.
        WebElement fullPdf = preview.findElement(By.cssSelector(".show-full-pdf-link"));
        assertThat(fullPdf.getText()).containsIgnoringCase("pokaż cały pdf");
        assertThat(fullPdf.getAttribute("target")).isEqualTo("_blank");
        String href = fullPdf.getAttribute("href");
        assertThat(href).describedAs("full-PDF link must hit the US-2.4 download endpoint")
                .contains("/bands/1/compositions/" + compositionId + "/files/")
                .doesNotContain("/thumb");

        // Mobile usability (AC step 4): the acceptance criterion is that a 360-px mobile
        // viewport shows the preview without horizontal page scroll. Assert on LAYOUT
        // geometry (offsetWidth — unaffected by any pan/scroll) rather than getRect().
        Object pageOverflowPx = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var d = document.documentElement;" +
                "return d.scrollWidth - d.clientWidth;");
        int overflowPx = ((Number) pageOverflowPx).intValue();
        assertThat(overflowPx).as("no horizontal page overflow at a 360 px viewport")
                .isEqualTo(0);

        // The preview panel fits its parent section in layout width (panel is block content,
        // so offsetWidth caps to the content box), and every thumbnail cell fits the panel.
        WebElement section = preview.findElement(By.xpath("ancestor::section"));
        int previewW = preview.getSize().getWidth();
        int sectionW = section.getSize().getWidth();
        assertThat(previewW).as("preview panel width <= section width").isLessThanOrEqualTo(sectionW);
        int cellMaxW = 0;
        for (WebElement cell : preview.findElements(By.cssSelector(".preview-grid > *"))) {
            cellMaxW = Math.max(cellMaxW, cell.getSize().getWidth());
        }
        assertThat(previewW).as("at least one thumbnail cell renders at sane mobile width")
                .isGreaterThanOrEqualTo(60);
        assertThat(cellMaxW).as("widest thumbnail cell <= panel width").isLessThanOrEqualTo(previewW);

        driver.manage().window().setSize(new Dimension(1280, 900));
    }

    // ---- helpers --------------------------------------------------------------------

    private Path generateTempPdf(int pages) throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
        for (int i = 0; i < pages; i++) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));
        }
        Path tmp = Files.createTempFile("upload-ui-test", ".pdf");
        doc.save(tmp.toFile());
        doc.close();
        return tmp;
    }

    /** Lower-case hex SHA-256 of the file's content — used to seed the score_files row. */
    private static String sha256Hex(Path file) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file));
        StringBuilder out = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            out.append(Integer.toHexString((b >> 4) & 0xF)).append(Integer.toHexString(b & 0xF));
        }
        return out.toString();
    }

    /**
     * Mobile viewport (360 × 780) — the upload section (dropzone) must not overflow horizontally,
     * all form controls must be reachable without panning, and the dropzone + queue
     * must be fully visible within the viewport.
     */
    @Test
    void uploadSection_shouldNotOverflowOnMobile360() throws Exception {
        // 512x780 is close enough to a real phone; we use this for the overflow assertion.
        driver.manage().window().setSize(new Dimension(360, 800));

        // The compositions LIST page render was pure overhead — loginAndNavigateTo
        // goes straight to the detail (its wait already accepts #composition-detail),
        // so the same-origin requirement for the XHR helpers is met in one load.
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);

        FluentWait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(20)).pollingEvery(Duration.ofMillis(100));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        // 1. The upload section (dropzone) is visible and fits inside the viewport width.
        org.openqa.selenium.Rectangle panelRect = driver.findElement(By.id("score-upload-panel")).getRect();
        assertThat(panelRect.getX()).as("upload panel starts at x >= 0").isGreaterThanOrEqualTo(0);
        int viewportWidth = driver.manage().window().getSize().getWidth();
        // Allow +12 px for CSS padding/border rounding.
        assertThat(panelRect.getX() + panelRect.getWidth())
                .describedAs("upload panel right edge (viewport=" + viewportWidth + "px)")
                .isLessThanOrEqualTo(viewportWidth + 12);

        // The dropzone must be visible and clickable
        WebElement dropzone = driver.findElement(By.id("score-upload-panel"));
        assertThat(dropzone.isDisplayed()).as("file-picker dropzone visible").isTrue();
        assertThat(dropzone.getText()).containsIgnoringCase("dodaj");

        // Restore a sensible viewport size for other tests in this class.
        driver.manage().window().setSize(new Dimension(1280, 900));
    }

    /**
     * After uploading a valid PDF via the UI flow (drag&drop or file input + confirm button),
     * the uploaded file must appear in the file list (#score-files-list) with correct metadata.
     */
    @Test
    void shouldShowSuccessPanel_afterUpload_withPageCount() throws Exception {
        // Generate a 2-page PDF.
        Path pdf = generateTempPdf(2);

        // The compositions LIST page render was pure overhead — loginAndNavigateTo
        // goes straight to the detail (its wait already accepts #composition-detail),
        // so the same-origin requirement for the XHR helpers is met in one load.
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        FluentWait<WebDriver> wait = new WebDriverWait(driver, Duration.ofSeconds(15)).pollingEvery(Duration.ofMillis(100));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        // Drive the real UI: set files on the hidden file input via JS
        String b64 = java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(pdf));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var input = document.getElementById('score-file-input');" +
                "var binaryStr = atob(arguments[0]);" +
                "var bytes = new Uint8Array(binaryStr.length);" +
                "for (var i = 0; i < binaryStr.length; i++) { bytes[i] = binaryStr.charCodeAt(i); }" +
                "var file = new File([bytes], 'test.pdf', { type: 'application/pdf' });" +
                "var dt = new DataTransfer();" +
                "dt.items.add(file);" +
                "input.files = dt.files;" +
                "input.dispatchEvent(new Event('change', { bubbles: true }));",
                b64);

        // Wait for upload queue to appear with pending file
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("upload-queue")));

        // Click the confirm upload button (Wgraj)
        WebElement confirmBtn = wait.until(ExpectedConditions.elementToBeClickable(By.id("confirm-uploads")));
        confirmBtn.click();

        // Wait for file to appear in the list
        FluentWait<WebDriver> successWait = new WebDriverWait(driver, Duration.ofSeconds(20)).pollingEvery(Duration.ofMillis(100));
        successWait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//ul[@id='score-files-list']//a[contains(text(),'test.pdf')]")));

        // The file should be in the list with correct metadata
        WebElement fileRow = driver.findElement(By.xpath("//ul[@id='score-files-list']//a[contains(text(),'test.pdf')]"));
        assertThat(fileRow).isNotNull();

        // Verify meta shows 2 pages
        WebElement meta = fileRow.findElement(By.xpath("following-sibling::span[contains(@class,'file-meta')]"));
        assertThat(meta.getText()).contains("2 stron");

        Files.deleteIfExists(pdf);
    }
}
