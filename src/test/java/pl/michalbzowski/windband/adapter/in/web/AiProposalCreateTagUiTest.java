package pl.michalbzowski.windband.adapter.in.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression UI test dla panelu akceptacji propozycji AI w
 * {@code compositions/detail.html} (zgłoszenie użytkownika 2026-11-17, dwa błędy).
 *
 * <p><b>Przyczyny pierwotne.</b> (1) Dialog używał bazowego {@code .app-modal}
 * — arkusza o max {@code 480px}; tabela czterech kolumn (głos / strony / pewność /
 * select instrumentu) była zgniatana do nieczytelnych pasków, a zagnieżdżone okno
 * scrolla {@code 320px} obcinało nagłówki. (2) {@code createBandInstrumentTag()}
 * wołał {@code getCookieLocal()}, ale helper mieszka w IIFE INNEGO bloku
 * {@code <script>} (panel uploadu) — poza zasięgiem; każdy klik „+ utwórz tag”
 * sypał ReferenceError połknięty przez globalny {@code window.onerror}, więc
 * przycisk sprawiał wrażenie martwego.
 *
 * <p><b>Co pilnuje ten test.</b> Analiza SUCCEEDED z prawdziwym artepektem:
 * jeden głos dopasowany do instrumentu zespołu, drugi nie. Modal musi być
 * realnie szeroki na pulpicie, klik „+ utwórz tag” MUSI utworzyć instrument
 * w bazie I natychmiast podstawic go do selecta wiersza (chip i przycisk
 * znikają, toast success), a ponieważ aktualizacja jest chirurgiczna (bez
 * pełnego re-renderu) — stan checkboxów wybrany przez użytkownika musi
 * przetrwać operację.
 */
class AiProposalCreateTagUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(20);
    /** 'Saksofon' to instrument bandu 1 (secie data.sql); gwarantujemy go INSERTEM z NOT EXISTS. */
    private static final String MATCHED_ROLE = "Saksofon";

    private long compositionId;
    private long scoreFileId;

    private long seedSucceededAnalysis(String arrangementJson) throws Exception {
        String title = "Analiza AI — tworzenie tagu UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) "
                        + "VALUES (?, 'UX — regresja szerokości modala i tworzenia tagu', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        Path pdf = Path.of(System.getProperty("java.io.tmpdir"), "windband-ai-tag",
                UUID.randomUUID() + ".pdf");
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, new byte[] {0x25, 0x50, 0x44, 0x46});
        jdbcTemplate.update(
                "INSERT INTO score_files (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) "
                        + "VALUES (?, 'application/pdf', 1024, 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', ?, 'Partytura (regresja).pdf', 9, CURRENT_TIMESTAMP)",
                compositionId, pdf.toString());
        scoreFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ?", Long.class, compositionId);

        Path artifact = Path.of(System.getProperty("java.io.tmpdir"), "windband-ai-tag",
                UUID.randomUUID() + "_arrangement.json");
        Files.writeString(artifact, arrangementJson);
        jdbcTemplate.update(
                "INSERT INTO score_analysis (composition_id, score_file_id, phase, runner_ref, error_message, "
                        + "arrangement_json_path, arrangement_musicxml_path, arrangement_mid_path, validation_txt_path, "
                        + "started_at, finished_at, created_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?, NULL, ?, NULL, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, scoreFileId, "sa-job-tag", artifact.toString());

        // Gwarantujemy istnienie instrumentu dopasowującego (data.sql bandu 1:
        // 'Saksofon' z sort_priority 3), niezależnie od kolejności secie w biegu suity.
        jdbcTemplate.update(
                "INSERT INTO instruments (name, description, sort_priority, band_id) "
                        + "SELECT ?, 'test fixture', 90, 1 WHERE NOT EXISTS "
                        + "(SELECT 1 FROM instruments WHERE band_id = 1 AND name = ?)",
                MATCHED_ROLE, MATCHED_ROLE);
        return compositionId;
    }

    private String displayOf(JavascriptExecutor js, String id) {
        return (String) js.executeScript(
                "var el = document.getElementById(arguments[0]);"
                        + "return el ? window.getComputedStyle(el).display : null;",
                id);
    }

    private boolean visible(JavascriptExecutor js, String id) {
        String d = displayOf(js, id);
        return d != null && !d.isEmpty() && !"none".equals(d);
    }

    private boolean analyzeDialogOpen(JavascriptExecutor js) {
        Boolean open = (Boolean) js.executeScript(
                "var d = document.getElementById('analyze-dialog');"
                        + "return d && (d.open === true || d.hasAttribute('open'));");
        return Boolean.TRUE.equals(open);
    }

    /** Gate CSS — ten sam wyścig co w AnalysisResumeUiTest: przed app.css „hidden” nie chowa. */
    private void waitForStylesheetControl(JavascriptExecutor js, WebDriverWait wait) {
        wait.until(d -> "none".equals(displayOf(js, "analyze-result-panel")));
    }

    @Test
    @DisplayName("Szeroki modal analizy AI; „+ utwórz tag” tworzy instrument i podstawia go do selecta bez utraty zaznaczeń")
    void createTagButtonCreatesInstrumentAndFillsSelect() throws Exception {
        String unmatchedRole = "Sakshorn barytonowy " + UUID.randomUUID().toString().substring(0, 8);
        seedSucceededAnalysis("""
                [
                  {"role":"%s","pageFrom":1,"pageTo":4,"confidence":0.91},
                  {"role":"%s","pageFrom":5,"pageTo":9,"confidence":0.55}
                ]
                """.formatted(MATCHED_ROLE, unmatchedRole));

        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        driver.manage().window().setSize(new Dimension(1440, 1000));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        waitForStylesheetControl(js, wait);

        WebElement actionBtn = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id("ai-analysis-action-btn")));
        assertThat(actionBtn.getText()).as("SUCCEEDED → karta oferuje otwarcie propozycji")
                .containsIgnoringCase("Otwórz propozycje AI");
        actionBtn.click();
        wait.until(d -> analyzeDialogOpen(js));
        wait.until(d -> visible(js, "analyze-accept-panel"));

        // (1) CZYTELNOŚĆ: wariant app-modal-wide — na pulpicie pełna szerokość
        // min(1080px, 94vw), nie bazowa czapka 480px. Asercja liczona z realnego
        // viewportu, żeby nie zależała od domyślnego rozmiaru okna headless.
        Map<?, ?> box = jsMap(js.executeScript(
                "var r = document.querySelector('#analyze-dialog .app-modal-content').getBoundingClientRect();"
                        + "return {'w': r.width, 'vw': window.innerWidth};"));
        double width = ((Number) box.get("w")).doubleValue();
        double viewport = ((Number) box.get("vw")).doubleValue();
        double expected = Math.min(1080.0, viewport * 0.94);
        assertThat(width)
                .as("modal analizy AI musi być szeroki (oczekiwane ~%.0fpx z viewportu %.0fpx, było %.0fpx)",
                        expected, viewport, width)
                .isGreaterThan(600.0)
                .isGreaterThanOrEqualTo(expected - 8.0);

        // Panel propozycji czyści się z PRAWDZIWEGO /proposals (SUCCEEDED to stan
        // terminalny — /analysis/latest nie napędza tu lifecycle, więc bez podszywki fetch).
        wait.until(d -> Long.valueOf(2L).equals(js.executeScript(
                "return document.querySelectorAll('.ai-row-select').length;")));
        Long saksofonId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", Long.class, MATCHED_ROLE);
        assertThat(js.executeScript(
                "return document.querySelector('.ai-row-select[data-idx=\"0\"]').value;"))
                .as("głos dopasowany ma wybrany instrument zespołu")
                .isEqualTo(String.valueOf(saksofonId));

        // (2) NIEDOPASOWANY WIERSZ: placeholder + chip + przycisk (przed naprawą klik
        // „+ utwórz tag” był martwy przez ReferenceError w scope'ie CSRF-helpera).
        assertThat(js.executeScript(
                "var s = document.querySelector('.ai-row-select[data-idx=\"1\"]');"
                        + "return s.options[s.selectedIndex].value;"))
                .as("niedopasowany głos startuje na pustym placeholderze")
                .isEqualTo("");
        assertThat(js.executeScript(
                "return !!document.querySelector('.ai-row-select[data-idx=\"1\"]').closest('td')"
                        + ".querySelector('.ai-row-nomatch');"))
                .isEqualTo(Boolean.TRUE);
        WebElement addTag = wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector(".ai-add-tag-btn")));
        assertThat(addTag.getText()).contains("utwórz tag");

        // Użytkownik ODZNACZA wiersz dopasowany — pełne re-render panelu (stara
        // ścieżka) zerowałoby ten stan; aktualizacja chirurgiczna musi go zachować.
        js.executeScript("document.querySelector('.ai-row-cb[data-idx=\"0\"]').click();");
        assertThat(js.executeScript(
                "return document.querySelector('.ai-row-cb[data-idx=\"0\"]').checked;"))
                .isEqualTo(Boolean.FALSE);

        addTag.click();

        // Tag REALNIE powstał w bazie zespołu — sedno zgłoszenia („nic się nie dzieje”).
        wait.until(d -> !jdbcTemplate.queryForList(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", unmatchedRole).isEmpty());
        Long createdId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", Long.class, unmatchedRole);

        // ...i od razu siedzi w selectcie, chip + przycisk znikają.
        wait.until(d -> {
            Map<?, ?> st = jsMap(js.executeScript("""
                    var s = document.querySelector('.ai-row-select[data-idx="1"]');
                    var td = s.closest('td');
                    return {value: s.value, text: s.options[s.selectedIndex].text,
                            filled: s.classList.contains('ai-row-filled'),
                            nomatch: !!td.querySelector('.ai-row-nomatch'),
                            btn: !!td.querySelector('.ai-add-tag-btn')};"""));
            return String.valueOf(createdId).equals(st.get("value"))
                    && Boolean.FALSE.equals(st.get("nomatch"))
                    && Boolean.FALSE.equals(st.get("btn"));
        });
        Map<?, ?> rowState = jsMap(js.executeScript("""
                var s = document.querySelector('.ai-row-select[data-idx="1"]');
                var td = s.closest('td');
                return {value: s.value, text: s.options[s.selectedIndex].text,
                        filled: s.classList.contains('ai-row-filled'),
                        nomatch: !!td.querySelector('.ai-row-nomatch'),
                        btn: !!td.querySelector('.ai-add-tag-btn')};"""));
        assertThat(rowState.get("text")).isEqualTo(unmatchedRole);
        assertThat(rowState.get("filled")).isEqualTo(Boolean.TRUE);

        // Feedback dla użytkownika: toast success + brak ścieżki błędu.
        wait.until(d -> Boolean.TRUE.equals(js.executeScript(
                "return Array.from(document.querySelectorAll('#toast-container .toast'))"
                        + ".some(function (t) { return t.textContent.indexOf('Utworzono tag') >= 0; });")));
        assertThat(js.executeScript(
                "return document.getElementById('ai-accept-error').classList.contains('hidden');"))
                .as("ścieżka sukcesu nie zostawia komunikatu błędu")
                .isEqualTo(Boolean.TRUE);

        // Stan checkboxów ocalał (brak pełnego re-renderu po utworzeniu tagu).
        assertThat(js.executeScript(
                "return document.querySelector('.ai-row-cb[data-idx=\"0\"]').checked;"))
                .as("odznaczenie wiersza dopasowanego przetrwało tworzenie tagu")
                .isEqualTo(Boolean.FALSE);
        assertThat(js.executeScript(
                "return document.querySelector('.ai-row-cb[data-idx=\"1\"]').checked;"))
                .as("wiersz z nowym tagiem pozostaje zaznaczony do akceptacji")
                .isEqualTo(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsMap(Object value) {
        return (Map<String, Object>) value;
    }
}
