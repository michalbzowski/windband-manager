package pl.michalbzowski.windband.adapter.in.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.Select;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UX zgłoszenie 2026-11-18: na mobile panel propozycji AI był "za szeroki"
 * (tabela wymuszała poziome scrollowanie) i po ręcznym wyborze instrumentu
 * "za wąski" — layout tabeli zależał od treści, bo chip ostrzeżzenia i
 * przycisk tagu zmieniały szerokośc kolumny w czasie dodawania lub usuwania chipa.
 *
 * <p>Umowa UI po naprawie: pełna widoczność każdego elementu na mobile ORAZ
 * desktopie, zero przewijania poziomego, stabilne szerokości wierszy
 * przy zmianie treści w komórce, duże cele dotyku (select i przycisk tagu).</p>
 */
class AiProposalsCardResponsiveUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(30);
    // Guaranteed band-1 instrument for the matched rows (data.sql / INSERT below).
    private static final String MATCHED_ROLE = "Saksofon";
    private long compositionId;
    private long analysisId;

    private void seedSucceededAnalysis() throws Exception {
        String unmatchedRole = "Sakshorn barytonowy " + UUID.randomUUID().toString().substring(0, 8);
        String title = "Analiza AI - duplikaty ról UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) "
                        + "VALUES (?, 'UX — regresja duplikowanych głosów', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        Path pdf = Path.of(System.getProperty("java.io.tmpdir"), "windband-ai-dup",
                UUID.randomUUID() + ".pdf");
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, new byte[] {0x25, 0x50, 0x44, 0x46});
        jdbcTemplate.update(
                "INSERT INTO score_files (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) "
                        + "VALUES (?, 'application/pdf', 1024, 'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd', ?, 'Partytura (regresja).pdf', 10, CURRENT_TIMESTAMP)",
                compositionId, pdf.toString());
        long scoreFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ?", Long.class, compositionId);

        // Kształt produkcji („Amerland"): ta sama rola dwa razy, inne zakresy stron;
        // plus jeden głos bez tagu zespołu (wybór ręczny).
        Path artifact = Path.of(System.getProperty("java.io.tmpdir"), "windband-ai-dup",
                UUID.randomUUID() + "_arrangement.json");
        Files.writeString(artifact, String.format("""
                [
                  {"role":"%s","pageFrom":1,"pageTo":2,"confidence":0.9},
                  {"role":"%s","pageFrom":5,"pageTo":6,"confidence":0.82},
                  {"role":"%s","pageFrom":3,"pageTo":4,"confidence":0.44}
                ]
                """, MATCHED_ROLE, MATCHED_ROLE, unmatchedRole));

        jdbcTemplate.update(
                "INSERT INTO score_analysis (composition_id, score_file_id, phase, runner_ref, error_message, "
                        + "arrangement_json_path, arrangement_musicxml_path, arrangement_mid_path, validation_txt_path, "
                        + "started_at, finished_at, created_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', 'sa-job-dup', NULL, ?, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, scoreFileId, artifact.toString());
        analysisId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_analysis WHERE composition_id = ?", Long.class, compositionId);

        jdbcTemplate.update(
                "INSERT INTO instruments (name, description, sort_priority, band_id) "
                        + "SELECT ?, 'test fixture', 90, 1 WHERE NOT EXISTS "
                        + "(SELECT 1 FROM instruments WHERE band_id = 1 AND name = ?)",
                MATCHED_ROLE, MATCHED_ROLE);
        // Instrument do ręcznego wyboru w selectcie (wyróżniony nazwą).
        createTestBand1Instrument("Kongi testowe " + UUID.randomUUID().toString().substring(0, 6), 85);
    }

    private void openAiPanel() throws Exception {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
// Gate CSS: przed app.css „hidden" nie chowa — czekamy aż arkusz faktycznie steruje.
        wait.until(d -> "none".equals((String) js.executeScript(
                "var el = document.getElementById('analyze-result-panel');"
                        + "return el ? window.getComputedStyle(el).display : null;")));

        WebElement actionBtn = wait.until(ExpectedConditions.elementToBeClickable(
                org.openqa.selenium.By.id("ai-analysis-action-btn")));
        actionBtn.click();
        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "var d = document.getElementById('analyze-dialog');"
                        + "return d && (d.open === true || d.hasAttribute('open'));")));
        wait.until(d -> Long.valueOf(3L).equals(js.executeScript(
                "return document.querySelectorAll('.ai-row-select').length;")));
    }

    private double jdouble(String script) {
        return ((Number) ((JavascriptExecutor) driver).executeScript(script)).doubleValue();
    }

    @Test
    @DisplayName("mobile 390px: karta AI cała w ekranie — bez przewijania poziomego, chip i select widoczne")
    void mobile_fullVisibility_noHorizontalOverflow() throws Exception {
        seedSucceededAnalysis();
        driver.manage().window().setSize(new Dimension(390, 844));
        openAiPanel();

        double innerW = jdouble("return window.innerWidth;");
        assertThat(jdouble("return document.documentElement.scrollWidth;"))
                .as("brak przewijania poziomego dokumentu").isLessThanOrEqualTo(innerW + 2);

        WebElement table = driver.findElement(By.cssSelector(".ai-proposals-table"));
        assertThat(table.getRect().getWidth())
                .as("tabela w ekranie i o realnej szerokości karty")
                .isBetween(300, (int) innerW + 1);

        WebElement chip = driver.findElement(By.cssSelector(".ai-row-nomatch"));
        assertThat(chip.isDisplayed()).as("chip ostrzezenia widoczny").isTrue();
        double chipLeft = chip.getRect().getX();
        double chipRight = chipLeft + chip.getRect().getWidth();
        assertThat(chipLeft).as("chip w krawedzi ekranu od lewej").isGreaterThanOrEqualTo(-1.0);
        assertThat(chipRight).as("chip w krawedzi ekranu do prawej").isLessThanOrEqualTo(innerW + 1.0);

        WebElement select = driver.findElement(By.cssSelector(".ai-row-select[data-idx=\"2\"]"));
        assertThat(select.isDisplayed()).as("select widoczny na mobile").isTrue();
        assertThat(select.getRect().getWidth())
                .as("select na pełną szerokośc (cel dotyku)").isGreaterThanOrEqualTo(250);

        assertThat(driver.findElement(By.cssSelector(".ai-cell-role .ai-role-name")).isDisplayed())
                .as("nazwa glosu widoczna").isTrue();
        assertThat(driver.findElement(By.cssSelector(".ai-cell-pages")).getText())
                .as("numeracja stron widoczna (3–4)").matches("\\d+\\s*[-\u2013]\\s*\\d+");
        assertThat(driver.findElement(By.cssSelector(".ai-conf")).isDisplayed())
                .as("odznaka pewnosci widoczna").isTrue();
        // Etykiety komorek (mobile zamiast thead):
        String content = (String) ((JavascriptExecutor) driver).executeScript(
                "var el = document.querySelector('.ai-cell-inst'); return getComputedStyle(el, '::before').content;");
        assertThat(content.toLowerCase()).as("etykieta 'Instrument' obecna").contains("instrument");
    }

    @Test
    @DisplayName("mobile: wybor instrumentu — chip znika, a karta nie zmienia szerokośc")
    void mobile_pickInstrument_layoutStaysStable() throws Exception {
        seedSucceededAnalysis();
        driver.manage().window().setSize(new Dimension(390, 844));
        openAiPanel();

        WebElement table = driver.findElement(By.cssSelector(".ai-proposals-table"));
        double wBefore = table.getRect().getWidth();
        WebElement select = driver.findElement(By.cssSelector(".ai-row-select[data-idx=\"2\"]"));
        WebElement row = (WebElement) ((JavascriptExecutor) driver).executeScript(
                "return arguments[0].closest('tr');", select);
        double rBefore = row.getRect().getWidth();
        WebElement chip = driver.findElement(By.cssSelector(".ai-row-nomatch"));

        String pickName = new Select(select).getOptions().stream()
                .map(o -> o.getText()).filter(t -> t.startsWith("Kongi"))
                .findFirst().orElseThrow(() -> new AssertionError("brak testowego instrumentu w selectcie"));
        new Select(select).selectByVisibleText(pickName);

        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        wait.until(ExpectedConditions.invisibilityOf(chip));
        assertThat(new Select(select).getFirstSelectedOption().getText()).isEqualTo(pickName);
        assertThat((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return arguments[0].classList.contains('ai-row-filled');", select))
                .as("potwierdzenie wizualne wyboru (ai-row-filled)").isTrue();

        double wAfter = table.getRect().getWidth();
        double rAfter = row.getRect().getWidth();
        assertThat(Math.abs(wBefore - wAfter))
                .as("stabilna szerokoś tabeli po usunieciu chipa").isLessThanOrEqualTo(2.0);
        assertThat(Math.abs(rBefore - rAfter))
                .as("stabilna szerokoś wiersza po usunieciu chipa").isLessThanOrEqualTo(2.0);
    }

    @Test
    @DisplayName("desktop 1400px: 4 kolumny widoczne bez cięcia, stabilne po wyborze instrumentu")
    void desktop_fourColumnsVisible_stableAfterPick() throws Exception {
        seedSucceededAnalysis();
        driver.manage().window().setSize(new Dimension(1400, 900));
        openAiPanel();

        double innerW = jdouble("return window.innerWidth;");
        assertThat(jdouble("return document.documentElement.scrollWidth;"))
                .as("brak przewijania poziomego na desktopie").isLessThanOrEqualTo(innerW + 2);

        List<WebElement> rows = driver.findElements(By.cssSelector(".ai-proposals-table tbody tr"));
        assertThat(rows).as("min 2 wiersze propozycji").hasSizeGreaterThanOrEqualTo(2);
        for (String cell : new String[] {".ai-cell-role", ".ai-cell-pages", ".ai-cell-conf", ".ai-cell-inst"}) {
            double w = driver.findElement(By.cssSelector(".ai-proposals-table tbody tr " + cell)).getRect().getWidth();
            assertThat(w).as("kolumna " + cell + " ma realna szerokoś").isGreaterThan(20.0);
        }

        WebElement table = driver.findElement(By.cssSelector(".ai-proposals-table"));
        double wBefore = table.getRect().getWidth();
        WebElement select = driver.findElement(By.cssSelector(".ai-row-select[data-idx=\"2\"]"));
        WebElement chip = driver.findElement(By.cssSelector(".ai-row-nomatch"));
        String pickName = new Select(select).getOptions().stream()
                .map(o -> o.getText()).filter(t -> t.startsWith("Kongi"))
                .findFirst().orElseThrow(() -> new AssertionError("brak testowego instrumentu w selectcie"));
        new Select(select).selectByVisibleText(pickName);

        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        wait.until(ExpectedConditions.invisibilityOf(chip));
        assertThat(Math.abs(wBefore - table.getRect().getWidth()))
                .as("stabilna szerokoś tabeli po usunieciu chipa").isLessThanOrEqualTo(3);
    }
}
