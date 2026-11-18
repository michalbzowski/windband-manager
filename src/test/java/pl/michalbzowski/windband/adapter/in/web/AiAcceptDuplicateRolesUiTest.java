package pl.michalbzowski.windband.adapter.in.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regresja akceptacji propozycji AI z duplikowaną rolą głosu
 * (zgłoszenie produkcyjna 2026-11-18, utwór „Amerland”, analiza id=31): arrangement.json
 * zawierał tę samą role („Puzon I/II/III B♭", „Sakshorn barytonowy")
 * powtórzoną z różnymi zakresami stron. Serwis akceptacji indeksował propozycje
 * po samej roli (putIfAbsent), więc każdy kolejny wiersz tej roli sypał „Zakres
 * stron nie zgadza się" → HTTP 400, a komunikat był niewidoczny (JS czytał
 * payload.message, serwer zwracał errorBody pod kluczem error).
 *
 * <p>Druga połówka zgłoszenia: wybór instrumentu ręcznie w selectcie nie
 * zostawiał żadnego widocznego potwierdzenia („niby zaznaczony, a wartości
 * nie widać") — tu pilnujemy stanu (value + zielona ramka ai-row-filled, chip
 * ostrzezenia znika) po zdarzeniu change.</p>
 **/
class AiAcceptDuplicateRolesUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(30);
    /** 'Saksofon' istnieje w band 1 (data.sql / seed testu) — gwarantujemy go INSERT-em. */
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsMap(Object value) {
        return (Map<String, Object>) value;
    }

    private long partRowsSafe(JavascriptExecutor js) {
        try {
            return (Long) js.executeScript(
                    "return document.querySelectorAll('#existing-parts tbody tr[data-part-id]').length;");
        } catch (org.openqa.selenium.WebDriverException racingReload) {
            return -1L;
        }
    }

    @Test
    @DisplayName("Rola powtórzona z różnymi zakresami + ręczny wybór: akceptacja zapisuje WSZYSTKIE partie, bez 400")
    void repeatedRoleAndManualPickAllAccepted() throws Exception {
        seedSucceededAnalysis();
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        driver.manage().window().setSize(new Dimension(1280, 1000));
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

        Long saksofonId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", Long.class, MATCHED_ROLE);

        // Wiersze dopasowane (ta sama rola!): selecty startują NA INSTRUMENCIE, nie na pusto.
        assertThat(js.executeScript("return document.querySelector('.ai-row-select[data-idx=\"0\"]').value;"))
                .isEqualTo(String.valueOf(saksofonId));
        assertThat(js.executeScript("return document.querySelector('.ai-row-select[data-idx=\"1\"]').value;"))
                .isEqualTo(String.valueOf(saksofonId));

        // (2) RĘCZNY WYBÓW w niedopasowanym wierszu — sedno: wybór MUSI zostać zarejestrowany
        // i potwierdzony wizualnie (poprzednio: „niby zaznaczony, a wartości nie widać").
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> instruments =
                (List<Map<String, Object>>) js.executeScript(
                "return Array.from(document.querySelectorAll('.ai-row-select[data-idx=\"2\"] option')).map("
                        + "function (o) { return {v: o.value, t: o.textContent}; });");
        String kongiName = (String) instruments.stream()
                .filter(o -> ((String) o.get("t")).startsWith("Kongi testowe"))
                .findFirst().orElseThrow(() -> new AssertionError("brak instrumentu testowego w selectcie"))
                .get("t");
        String kongiValue = (String) instruments.stream()
                .filter(o -> ((String) o.get("t")).equals(kongiName))
                .findFirst().orElseThrow().get("v");

        js.executeScript("var s = document.querySelector('.ai-row-select[data-idx=\"2\"]');"
                + "s.value = arguments[0];"
                + "s.dispatchEvent(new Event('change', {bubbles: true}));", kongiValue);

        Map<String, Object> rowState = jsMap(js.executeScript("""
                var s = document.querySelector('.ai-row-select[data-idx="2"]');
                var td = s.closest('td');
                return {value: s.value,
                        text: s.options[s.selectedIndex ? s.selectedIndex : 0] ? s.options[s.selectedIndex].text : null,
                        filled: s.classList.contains('ai-row-filled'),
                        nomatch: !!td.querySelector('.ai-row-nomatch'),
                        btn: !!td.querySelector('.ai-add-tag-btn')};"""));
        assertThat(rowState.get("value")).as("wybrany instrument jest trzymany w selectcie")
                .isEqualTo(kongiValue);
        assertThat(rowState.get("text")).as("nazwa wybranego instrumentu jest widoczna jako wartość selectu")
                .isEqualTo(kongiName);
        assertThat(rowState.get("filled")).as("widoczne potwierdzenie wyboru (zielona ramka)")
                .isEqualTo(Boolean.TRUE);
        assertThat(rowState.get("nomatch")).as("chip ostrzezenia znika po wyborze")
                .isEqualTo(Boolean.FALSE);

        // (3) AKCEPTACJA — przed naprawą: 400 („Zakres stron ... nie zgadza się" na drugim
        // wierszu „Saksofon"), a komunikat był niewidoczny. Teraz: wszystko trafia do bazy.
        wait.until(ExpectedConditions.elementToBeClickable(org.openqa.selenium.By.id("ai-accept-btn")))
                .click();

        // JS przeładowuje stronę po ~2,5 s sukcesu — czekamy na realne wiersze partii.
        wait.until(d -> partRowsSafe(js) >= 3);
        assertThat(partRowsSafe(js)).as("dokładnie trzy zaakceptowane partie (duplikat roli włącznie)")
                .isEqualTo(3L);

        List<Map<String, Object>> parts = jdbcTemplate.queryForList(
                "SELECT instrument_role, instrument_id, page_from, page_to, source "
                        + "FROM composition_instruments WHERE composition_id = ? ORDER BY page_from",
                compositionId);
        assertThat(parts).hasSize(3);
        assertThat(parts)
                .extracting(p -> String.valueOf(p.get("page_from")))
                .containsExactly("1", "3", "5");
        // Dwa bloki tej samej roli — sedno zgłoszenia produkcji.
        assertThat(parts.stream().filter(p -> MATCHED_ROLE.equals(p.get("instrument_role"))).count())
                .as("oba zakresy powtórzonej roli zostały zaakceptowane")
                .isEqualTo(2L);
        // Ręczny wybór (głos bez tagu) → partia z wybranym instrumentem (HYBRID, nie AI).
        Map<String, Object> manual = parts.stream()
                .filter(p -> String.valueOf(p.get("page_from")).equals("3"))
                .findFirst().orElseThrow();
        assertThat(String.valueOf(manual.get("source"))).isEqualTo("HYBRID");
        Long kongiId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", Long.class, kongiName);
        assertThat(manual.get("instrument_id")).isEqualTo(kongiId);

        // Brak ścieżki błędu (brak 400!) — przed naprawą tu siedział „Akceptacja nie powiodła się: HTTP 400".
        assertThat(js.executeScript(
                "return document.getElementById('ai-accept-error').classList.contains('hidden');"))
                                .as("sćieżka akceptacji kończy się bez komunikatu błędu")

                .isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("Frontend czyta komunikat błędu z klucza error (kontrakt GlobalExceptionHandler)")
    void errorBodyContractReadByFrontend() {
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("templates/compositions/detail.html")) {
            assertThat(in).as("detail.html dostępny jako zasób testowy").isNotNull();
            String html = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(html)
                    .as("akceptacja musi czytać payload.error (serwer zwraca errorBody pod tym kluczem; "
                            + "stary kod czytał payload.message i widział tylko 'HTTP 400')")
                    .contains("payload.error || payload.message");
        } catch (java.io.IOException e) {
            throw new AssertionError("nie udało się odczytać detail.html", e);
        }
    }
}
