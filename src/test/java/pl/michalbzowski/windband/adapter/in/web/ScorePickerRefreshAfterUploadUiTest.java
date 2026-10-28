package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #249 — after uploading a score the picker in “Dodaj głos (strony w nutach)” still showed
 * only “— wybierz plik (nieobowiązkowo) —”; the file became selectable only after a reload.
 *
 * <p>Both pickers on the page are rendered server-side from {@code scoreFiles}, while the upload
 * itself is a background POST that only appends a row to the file list. This test drives the real
 * upload (file input → “Wgraj”), not an XHR, because the defect lives in the client-side
 * bookkeeping that the raw request bypasses.
 */
class ScorePickerRefreshAfterUploadUiTest extends UiTestBase {

    @Test
    void uploadedScoreIsSelectableWithoutReload() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String title = "Świeży skan " + marker;
        jdbcTemplate.update("""
                INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES (?, null, null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, title);
        Long compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        // name is asserted later as the option label, so it has to be unique per run
        Path pdf = tempPdfNamed("skan-" + marker + ".pdf", 2);
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(1280, 900));
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        org.openqa.selenium.support.ui.FluentWait<org.openqa.selenium.WebDriver> wait =
                new WebDriverWait(driver, Duration.ofSeconds(25)).pollingEvery(Duration.ofMillis(200));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("score-upload-panel")));

        // the dropzone input is .visually-hidden by design; Selenium refuses to type into a
        // hidden node, so it is revealed for the sendKeys and the page's own change handler runs
        // exactly as with a real click on “Wybierz plik”.
        WebElement fileInput = driver.findElement(By.id("score-file-input"));
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var i = arguments[0]; i.classList.remove('visually-hidden');"
              + "i.style.display='block'; i.style.opacity='1'; i.style.position='static';", fileInput);
        fileInput.sendKeys(pdf.toAbsolutePath().toString());
        WebElement confirm = wait.until(d -> d.findElement(By.id("confirm-uploads")));
        wait.until(d -> confirm.isEnabled());
        confirm.click();

        // the file is in the database and on the visible list before we touch the picker
        wait.until(d -> !driver.findElements(By.cssSelector(".file-list-row")).isEmpty());
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, compositionId);
        assertThat(fileId).as("upload must have persisted the score").isNotNull();

        // 1) “Dodaj głos” — the modal reported in the issue
        wait.until(d -> d.findElement(By.id("open-add-part-modal-btn"))).click();
        wait.until(d -> d.findElement(By.id("part-score-file-picker")).isDisplayed());
        assertThat(optionLabels("part-score-file-picker"))
                .as("issue #249: the file uploaded minutes ago must be selectable right away")
                .anyMatch(label -> label.contains("skan-" + marker) && label.contains("2 stron"));
        assertThat(optionValues("part-score-file-picker"))
                .as("and it carries the id the form posts")
                .contains(String.valueOf(fileId));

        // 2) the analysis picker had the same staleness
        assertThat(optionLabels("score-file-picker"))
                .as("the analysis picker is server-rendered the same way")
                .anyMatch(label -> label.contains("skan-" + marker));
    }

    /**
     * {@code WebElement#getText()} is empty for options inside a closed modal (nothing is
     * rendered), and the analysis picker lives in one — the DOM text is what matters here.
     */
    private java.util.List<String> optionLabels(String selectId) {
        return (java.util.List<String>) ((JavascriptExecutor) driver).executeScript(
                "return Array.from(document.querySelectorAll('#' + arguments[0] + ' option'))"
                        + ".map(function (o) { return o.textContent.trim(); });",
                selectId);
    }

    private java.util.List<String> optionValues(String selectId) {
        return options(selectId).stream().map(o -> o.getAttribute("value")).toList();
    }

    private java.util.List<WebElement> options(String selectId) {
        return driver.findElement(By.id(selectId)).findElements(By.tagName("option"));
    }

    private Path tempPdfNamed(String fileName, int pages) throws Exception {
        org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
        for (int i = 0; i < pages; i++) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));
        }
        Path dir = Files.createTempDirectory("wm249");
        Path out = dir.resolve(fileName);
        doc.save(out.toFile());
        doc.close();
        return out;
    }
}
