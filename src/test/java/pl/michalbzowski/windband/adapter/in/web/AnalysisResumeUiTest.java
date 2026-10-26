package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UX regression — "nie ma jak wrócić do trwającej (zakończonej) analizy".
 *
 * <p>Demande użytkownika: zamknięcie okna / wylogowanie nie może blokować dostępu do
 * stanu ostatnich analiz utworu. Gdy strona utworu wie, że istnieje wiersz
 * {@code score_analysis} (RUNNING, SUCCEEDED albo FAILED), MUSI to pokazać karta
 * statusu nad akcjami, a modal {@code #analyze-dialog} otwiera się w odpowiadającym
 * stanie: śledzenie postępu / wynik z propozycjami / błąd z ponowieniem.</p>
 *
 * <p>Kontekst bugów, które testy zamykają:</p>
 * <ul>
 *   <li>{@code openAnalyzeModal()} zawsze resetował do panelu startowego — nie dało się
 *       podłączyć do trwającej analizy ani otworzyć wyniku po powrocie;</li>
 *   <li>karty statusu ostatniej analizy nie istniały poza dialogiem (brak nawigacji);</li>
 *   <li>klasa {@code hidden} NIE BYŁA zdefiniowana w CSS (Pico style only the
 *       [hidden] ATTRIBUTE) — "schowane" panele modala renderowały się na wierzchu.</li>
 * </ul>
 */
class AnalysisResumeUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private long compositionId;
    private long scoreFileId;

    @BeforeEach
    void seedCompositionWithAttachedScoreFile() {
        String title = "Analysis Resume UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'UX - powrot do trwajacej/zakonczonych analiz', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?",
                Long.class, title);

        String storagePath = Path.of(System.getProperty("java.io.tmpdir"), "windband-analysis-resume",
                UUID.randomUUID() + ".pdf").toString();
        jdbcTemplate.update(
                "INSERT INTO score_files " +
                        "(composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) " +
                        "VALUES (?, 'application/pdf', 1024, " +
                        "'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb' , " +
                        "? , 'Second Suite (regresja).pdf', 5, CURRENT_TIMESTAMP)",
                compositionId, storagePath);
        scoreFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ?", Long.class, compositionId);
    }

    private void seedAnalysis(String phase, String runnerRef, String errorMessage, String arrangementJsonPath) {
        jdbcTemplate.update(
                "INSERT INTO score_analysis (composition_id, score_file_id, phase, runner_ref, error_message, " +
                        "arrangement_json_path, arrangement_musicxml_path, arrangement_mid_path, validation_txt_path, " +
                        "started_at, finished_at, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, scoreFileId, phase, runnerRef, errorMessage, arrangementJsonPath);
    }

    private String displayOf(JavascriptExecutor js, String id) {
        return (String) js.executeScript(
                "var el = document.getElementById(arguments[0]);" +
                        "return el ? window.getComputedStyle(el).display : null;",
                id);
    }

    private boolean visible(JavascriptExecutor js, String id) {
        String d = displayOf(js, id);
        return d != null && !d.isEmpty() && !"none".equals(d);
    }

    @Test
    void runningAnalysis_pageShowsTrackingCard_andModalAttachesToTheRun() {
        seedAnalysis("RUNNING", "sa-job-42", null, null);
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        // Karta statusu: stan RUNNING widoczny na stronie (po załadowaniu / powrocie z wylogowaniem).
        WebElement actionBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("ai-analysis-action-btn")));
        assertThat(actionBtn.isDisplayed())
                .as("status card action button renders when a RUNNING analysis exists")
                .isTrue();
        assertThat(actionBtn.getText()).containsIgnoringCase("śledź postęp");
        String pageText = driver.findElement(By.cssSelector(".composition-header-article")).getText();
        assertThat(pageText).containsIgnoringCase("Analiza AI toczy się");

        // Kliknięcie podłącza do TRWAJĄCEJ analizy — modal w trybie śledzenia, nie startu.
        // UWAGA: endpoint /analysis/latest NAPĘDZA cykl życia (poll() obserwuje runner) i w profilu
        // testowym (stub single-shot) nie da się utrzymać rzędu RUNNING w bazie — dlatego fixujemy
        // window.fetch na dokładnym kontrakcie DTO, żeby testować ROUTING UI deterministycznie.
        js.executeScript(
                "window.__realFetch = window.fetch.bind(window);" +
                        "window.fetch = function (url, opts) {" +
                        "    if (String(url).indexOf('/analysis/latest') !== -1) {" +
                        "        return Promise.resolve({" +
                        "            ok: true, status: 200," +
                        "            json: function () { return Promise.resolve({" +
                        "                id: 999, phase: 'RUNNING', runnerRef: 'sa-probe'," +
                        "                arrangementJsonPath: 'null', arrangementMidPath: 'null'," +
                        "                arrangementMusicxmlPath: 'null', validationTxtPath: 'null' }); }" +
                        "        });" +
                        "    }" +
                        "    return window.__realFetch(url, opts);" +
                        "}");
        actionBtn.click();
        wait.until(d -> analyzeDialogOpen(js));
        wait.until(d -> visible(js, "analyze-progress-panel"));
        assertThat(visible(js, "analyze-progress-panel"))
                .as("progress panel is visible for a RUNNING analysis")
                .isTrue();
        assertThat(visible(js, "analyze-start-panel"))
                .as("start panel must NOT be the landing panel when an analysis is already running")
                .isFalse();
        String badge = driver.findElement(By.id("analyze-phase-badge")).getText();
        assertThat(badge).containsIgnoringCase("RUNNING");
    }

    @Test
    void succeededAnalysis_pageOffersResult_andModalShowsArtifactsAndProposals() throws IOException {
        // Artefakt zgodny z kontraktem JsonAiProposals — pusta lista głosów (poprawne "nie wykryto").
        Path artifact = Path.of(System.getProperty("java.io.tmpdir"), "windband-analysis-resume",
                UUID.randomUUID() + "_arrangement.json");
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, "[]");

        seedAnalysis("SUCCEEDED", "sa-job-43", null, artifact.toString());
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        WebElement actionBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("ai-analysis-action-btn")));
        assertThat(actionBtn.isDisplayed()).isTrue();
        assertThat(actionBtn.getText()).containsIgnoringCase("Otwórz propozycje AI");
        String pageText = driver.findElement(By.cssSelector(".composition-header-article")).getText();
        assertThat(pageText).containsIgnoringCase("zakończona sukcesem");

        actionBtn.click();
        wait.until(d -> analyzeDialogOpen(js));
        wait.until(d -> visible(js, "analyze-result-panel"));

        // Zakończona analiza: panel wyniku widoczny, start panel zasłonięty (reguła .hidden działa).
        assertThat(visible(js, "analyze-result-panel")).isTrue();
        assertThat(visible(js, "analyze-start-panel")).isFalse();

        // Link do artefaktu + panel akceptacji propozycji AI (pusta lista = komunikat, nie błąd).
        boolean artifactLinkPresent = (Boolean) js.executeScript(
                "var ul = document.getElementById('artifact-links');" +
                        "if (!ul) { return false; }" +
                        "for (var i = 0; i < ul.children.length; i++) {" +
                        "    if (ul.children[i].textContent.indexOf('arrangement.json') !== -1) { return true; }" +
                        "}" +
                        "return false;");
        assertThat(artifactLinkPresent).as("result panel lists the arrangement.json artifact link").isTrue();

        wait.until(d -> visible(js, "analyze-accept-panel"));
        wait.until(d -> {
            Boolean m = (Boolean) js.executeScript(
                    "var w = document.getElementById('ai-proposals-wrap');" +
                            "return w && w.textContent.indexOf('nie wykryła głosów') !== -1;");
            return Boolean.TRUE.equals(m);
        });
        assertThat(true)
                .as("empty proposal list renders the informational message, not an error")
                .isTrue();
    }

    @Test
    void failedAnalysis_pageShowsError_cardRetryOpensStartPanelAgain() {
        seedAnalysis("FAILED", "sa-job-44", "Model nie zwrócił głosów (timeout).", null);
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        WebElement actionBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("ai-analysis-action-btn")));
        assertThat(actionBtn.isDisplayed()).isTrue();
        assertThat(actionBtn.getText()).containsIgnoringCase("Spróbuj ponownie");
        String pageText = driver.findElement(By.cssSelector(".composition-header-article")).getText();
        assertThat(pageText).containsIgnoringCase("nieudana");
        assertThat(pageText).containsIgnoringCase("Model nie zwrócił głosów");

        actionBtn.click();
        wait.until(d -> analyzeDialogOpen(js));
        wait.until(d -> visible(js, "analyze-retry-row"));

        // Błąd + wiersz ponowienia widoczne, start panel zasłonięty…
        assertThat(visible(js, "analyze-result-panel")).isTrue();
        assertThat(driver.findElement(By.id("analyze-error-text")).getText())
                .containsIgnoringCase("Model nie zwrócił głosów");
        assertThat(visible(js, "analyze-retry-row")).isTrue();

        // …a kliknięcie „ponów" wraca do panelu startowego.
        driver.findElement(By.id("analyze-retry-btn")).click();
        wait.until(d -> visible(js, "analyze-start-panel"));
        assertStartPanelVisible(js);
    }

    private void assertStartPanelVisible(JavascriptExecutor js) {
        assertThat(visible(js, "score-file-picker"))
                .as("start panel becomes interactive after retry (picker available)")
                .isTrue();
    }

    private boolean analyzeDialogOpen(JavascriptExecutor js) {
        Boolean open = (Boolean) js.executeScript(
                "var d = document.getElementById('analyze-dialog');" +
                        "return d && (d.open === true || d.hasAttribute('open'));");
        return Boolean.TRUE.equals(open);
    }
}
