package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test — "przycisk 🎼 Analizuj kliknięty, ale nic się nie dzieje".
 *
 * <p>{@code openAnalyzeModal()} in {@code compositions/detail.html} used to call
 * {@code openAppModal(dlg)} with the DIALOG ELEMENT, while the layout modal framework
 * ({@code fragments/layout.html → openAppModal(id)}) expects an ELEMENT ID STRING:
 * it does {@code document.getElementById(id); if (!dlg) return;} — so the element was
 * stringified to "[object HTMLDialogElement]", no dialog was found, and the function
 * returned SILENTLY (no error, no modal). Every other call site in the codebase passes
 * a string id; only this one did.</p>
 *
 * <p>This test reproduces the user's exact steps: open the composition detail page of a
 * composition that HAS an attached score file (so the button renders at all), click
 * "🎼 Analizuj", and assert the {@code #analyze-dialog} actually opens with the start
 * panel's file picker visible.</p>
 */
class AnalyzeButtonOpensModalUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long compositionId;

    @BeforeEach
    void seedCompositionWithAttachedScoreFile() {
        String title = "Analyze Button UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja - przycisk Analizuj musi otworzyc modal', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?",
                Long.class, title);

        // One attached score file — the metadata row is enough: the button's
        // th:if="${scoreFiles != null and !scoreFiles.isEmpty()}" reads the model list,
        // and the dialog's picker renders one <option> per row (no on-disk file needed).
        jdbcTemplate.update(
                "INSERT INTO score_files " +
                        "(composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) " +
                        "VALUES (?, 'application/pdf', 1024, " +
                        "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', " +
                        "?, 'Polonez (regresja).pdf', 5, CURRENT_TIMESTAMP)",
                compositionId, java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "windband-ui-analyze", UUID.randomUUID() + ".pdf").toString());
    }

    private boolean analyzeDialogOpen(JavascriptExecutor js) {
        Boolean open = (Boolean) js.executeScript(
                "var d = document.getElementById('analyze-dialog');" +
                        "return d && (d.open === true || d.hasAttribute('open'));\n");
        return Boolean.TRUE.equals(open);
    }

    @Test
    void analyzeButtonClick_opensTheDialog_withStartPanel() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        // Precondition: with an attached score file the actions bar renders the button.
        WebElement analyzeBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("analyze-composition-btn")));
        assertThat(analyzeBtn.isDisplayed())
                .as("'🎼 Analizuj' is rendered in the composition actions bar when a score file is attached")
                .isTrue();

        // THE regression: the click must actually OPEN #analyze-dialog
        // (before the fix openAppModal() received an element and silently no-oped).
        analyzeBtn.click();
        wait.until(d -> analyzeDialogOpen(js));
        assertThat(analyzeDialogOpen(js))
                .as("clicking '🎼 Analizuj' opens the #analyze-dialog")
                .isTrue();

        // And the user sees the start panel with a usable file picker.
        Boolean startPanelDisplayed = (Boolean) js.executeScript(
                "var sp = document.getElementById('analyze-start-panel');" +
                        "return sp && window.getComputedStyle(sp).display !== 'none';");
        Long pickerOptions = (Long) js.executeScript(
                "var p = document.getElementById('score-file-picker');" +
                        "return p ? p.options.length : 0;");
        assertThat(Boolean.TRUE.equals(startPanelDisplayed))
                .as("after the click, the analysis start panel is visible inside the dialog")
                .isTrue();
        assertThat(pickerOptions)
                .as("the file picker offers the attached score file (th:each over scoreFiles)")
                .isGreaterThan(0L);
    }
}
