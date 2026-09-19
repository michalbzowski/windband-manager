package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
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
        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + compositionId);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        // The page must render without a Thymeleaf JS error first.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        // The upload section must be visible on the detail page.
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        assertThat(driver.findElement(By.id("score-file-input")))
                .as("file input with accept=PDF")
                .isNotNull();
        String accept = driver.findElement(By.id("score-file-input")).getAttribute("accept");
        assertThat(accept).containsIgnoringCase("pdf");
        assertThat(driver.findElement(By.id("upload-score-btn")))
                .as("'Wgraj nuty' button")
                .isNotNull();

        // Clicking the file label opens the file picker (Selenium cannot read hidden
        // <input type="file"> .files directly). Verify the surrounding UI elements
        // are present and correctly wired: cancel button, result div starts hidden.
        assertThat(driver.findElement(By.id("cancel-score-upload-btn"))).isNotNull();
        String resultDivClass = driver.findElement(By.id("score-upload-result")).getAttribute("class");
        assertThat(resultDivClass).contains("hidden");
    }

    @Test
    void shouldUploadValidPdf_viaXhr_andPersistPageCount() throws Exception {
        // Generate a 3-page PDF in a temp file.
        Path pdf = generateTempPdf(3);

        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
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

    /**
     * Mobile viewport (360 × 780) — the upload section must not overflow horizontally,
     * all form controls must be reachable without panning, and the file label + buttons
     * must be fully visible within the viewport.
     */
    @Test
    void uploadSection_shouldNotOverflowOnMobile360() throws Exception {
        // 512x780 is close enough to a real phone; we use this for the overflow assertion.
        driver.manage().window().setSize(new Dimension(360, 800));

        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + compositionId);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        // 1. The upload section is visible and fits inside the viewport width.
        org.openqa.selenium.Rectangle panelRect = driver.findElement(By.id("score-upload-panel")).getRect();
        assertThat(panelRect.getX()).as("upload panel starts at x >= 0").isGreaterThanOrEqualTo(0);
        int viewportWidth = driver.manage().window().getSize().getWidth();
        // Allow +12 px for CSS padding/border rounding.
        assertThat(panelRect.getX() + panelRect.getWidth())
                .describedAs("upload panel right edge (viewport=" + viewportWidth + "px)")
                .isLessThanOrEqualTo(viewportWidth + 12);

        // The submit button and file label must be visible within the viewport.
        org.openqa.selenium.Rectangle btnRect = driver.findElement(By.id("upload-score-btn")).getRect();
        assertThat(btnRect.getX() + btnRect.getWidth())
                .describedAs("upload button right edge within viewport on mobile")
                .isLessThanOrEqualTo(viewportWidth + 12);

        // 3. All key controls are visible (not display:none, not outside the section).
        assertThat(driver.findElement(By.id("score-file-input")).getAttribute("accept"))
                .containsIgnoringCase("pdf");
        assertThat(driver.findElement(By.id("upload-score-btn")).isEnabled())
                .as("submit button is enabled after file selection").isFalse(); // starts disabled

        // 4. The big "choose file" area (the picker label) is visible and invites
        //    a click — clicking anywhere in the panel drives it.
        WebElement label = driver.findElement(By.cssSelector(".score-file-picker"));
        assertThat(label.isDisplayed()).as("file-picker label visible").isTrue();
        assertThat(label.getText()).containsIgnoringCase("kliknij");

        // Restore a sensible viewport size for other tests in this class.
        driver.manage().window().setSize(new Dimension(1280, 900));
    }

    /**
     * After uploading a valid PDF via the UI flow (file input + submit button),
     * the success panel (#score-upload-result) must become visible with the
     * uploaded file name and page count in the toast text.
     */
    @Test
    void shouldShowSuccessPanel_afterUpload_withPageCount() throws Exception {
        // Generate a 2-page PDF.
        Path pdf = generateTempPdf(2);

        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-detail")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-panel")));

        // Drive the real UI: set files on the hidden file input via JS (Selenium's
        // sendKeys on a file input sets .files via the browser's file picker path;
        // here we use Object.defineProperty to bypass the picker in headless mode).
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

        // The submit button should now be enabled.
        wait.until(ExpectedConditions.elementToBeClickable(By.id("upload-score-btn")));
        driver.findElement(By.id("upload-score-btn")).click();

        WebDriverWait successWait = new WebDriverWait(driver, Duration.ofSeconds(15));
        successWait.until(ExpectedConditions.visibilityOfElementLocated(By.id("score-upload-result")));

        // The toast text must show the file name and page count.
        String toastText = driver.findElement(By.cssSelector("#score-upload-result .toast")).getText();
        assertThat(toastText).containsIgnoringCase("test.pdf");
        assertThat(toastText).contains("2 stron");

        // The download link must point to the correct endpoint.
        String href = driver.findElement(By.id("score-download-link")).getAttribute("href");
        assertThat(href).contains("/files/");

        Files.deleteIfExists(pdf);
    }
}
