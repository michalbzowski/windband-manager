package pl.michalbzowski.windband.adapter.in.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flaga M1 „raport spójności AI” (app.ai-verify-report.enabled): E2E nowego widoku
 * /bands/{b}/compositions/{id}/verify-ai — kolejka głosów z werdyktami spójności,
 * korekta zakresu przed akceptacją (HYBRID), masowe „bezmianowe” przyjęcie z
 * pełnym rollbackiem przy głosie poza pageCount, oraz stan pusty przy braku analizy.
 *
 * <p>Flaga jest włączona w suite UI (application-test.yml); domyślne OFF + 404
 * pilnują testy jednostkowe (REST i kontroler widoku).</p>
 */
class AiVerifyReportUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final int PAGE_COUNT = 10;

    private long compositionId;

    /** Plik nut seedowanej analizy (potrzebny do partii w TEJ przestrzeni stron). */
    private long scoreFileId;

    /** Instrument bandu 1 rozwiązany z teamTag wiersza „Saksofon altowy” (regresja białego ekranu). */
    private long resolvedSaxInstrumentId;

    /** Dedykowany teamTag rozwiązany do powyższego instrumentu (chip → instrument w karcie). */
    private String resolvedSaxTag;

    /** Utwór + nuty (10 stron) + SUCCEEDED analiza z czterema głosami: dwa czyste, luka, przekroczenie. */
    private void seedSucceededAnalysis() throws Exception {
        String title = "Raport spójności UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) "
                        + "VALUES (?, 'UX — raport spójności AI', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);

        Path pdf = Path.of(System.getProperty("java.io.tmpdir"), "windband-verify-report",
                UUID.randomUUID() + ".pdf");
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, new byte[] {0x25, 0x50, 0x44, 0x46});
        jdbcTemplate.update(
                "INSERT INTO score_files (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at) "
                        + "VALUES (?, 'application/pdf', 1024, 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', ?, 'Partytura (raport).pdf', ?, CURRENT_TIMESTAMP)",
                compositionId, pdf.toString(), PAGE_COUNT);
        long scoreFileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE composition_id = ?", Long.class, compositionId);
        this.scoreFileId = scoreFileId;

        // 2-4 czysty; 5-7 sąsiedni (bez luki); 9-10 → luka po stronie 8 (ostrzeżenie);
        // 11-12 → poza pageCount=10 (błąd BEYOND_PAGES).
        // „Saksofon altowy” ma teamTag rozwiązany do dedykowanego instrumentu bandu 1 —
        // jedyny wiersz z przypisanym instrumentem (piastuje gałąź true renderowania wiersza).
        String saxTag = "Saksofony raportowe " + UUID.randomUUID().toString().substring(0, 6);
        Path artifact = Path.of(System.getProperty("java.io.tmpdir"), "windband-verify-report",
                UUID.randomUUID() + "_arrangement.json");
        Files.writeString(artifact, """
                [
                  {"role":"Trąbka I","pageFrom":2,"pageTo":4,"confidence":0.95},
                  {"role":"Tuba E","pageFrom":5,"pageTo":7,"confidence":0.85},
                  {"role":"Saksofon altowy","teamTag":"@@SAX_TAG@@","pageFrom":9,"pageTo":10,"confidence":0.8},
                  {"role":"Klarnet basowy","pageFrom":11,"pageTo":12,"confidence":0.75}
                ]
                """.replace("@@SAX_TAG@@", saxTag));

        jdbcTemplate.update(
                "INSERT INTO score_analysis (composition_id, score_file_id, phase, runner_ref, error_message, "
                        + "arrangement_json_path, arrangement_musicxml_path, arrangement_mid_path, validation_txt_path, "
                        + "started_at, finished_at, created_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', 'sa-job-verify', NULL, ?, NULL, NULL, NULL, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, scoreFileId, artifact.toString());

        // Instrument ręcznie do wyboru w korekcie (wyraźna nazwa testowa).
        createTestBand1Instrument("Klarnety raportowe " + UUID.randomUUID().toString().substring(0, 6), 85);
        // Dedykowany instrument równy teamTag — jedyny wiersz „rozwiązany” na stronie raportu.
        createTestBand1Instrument(saxTag, 90);
        resolvedSaxTag = saxTag;
        resolvedSaxInstrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = ?", Long.class, saxTag);
    }

    private void seedCompositionWithoutAnalysis() {
        String title = "Raport spójności pusty-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) "
                        + "VALUES (?, 'UX — stan pusty raportu', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);
    }

    private long partCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments WHERE composition_id = ?",
                Long.class, compositionId);
    }

    /** Indeks wiersza kolejkowego po nazwie roli (nie ufa się porządkowi renderowania). */
    private long rowIndexByRole(JavascriptExecutor js, String role) {
        return (Long) js.executeScript("""
                var li = Array.from(document.querySelectorAll('#verify-queue .verify-row'));
                for (var i = 0; i < li.length; i++) {
                    var h = li[i].querySelector('.verify-role');
                    if (h && h.textContent.trim() === arguments[0]) { return i; }
                }
                return -1;""", role);
    }

    @Test
    @DisplayName("raport spójności: korekta zakresu → HYBRID; masa z błędem → rollback; głosy poprawne → przyjęte")
    void reportWithCorrectionAndBulkRollback() throws Exception {
        seedSucceededAnalysis();
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        // (1) Wejście z karty utworu: link widoczny tylko przy zakończonej analizie i flamie ON.
        WebElement entry = wait.until(ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("ai-verify-entry")));
        entry.click();
        // Kolejka renderuje się przy wczesnym rysunku widoku; stan raportu (błędy/ostrzeżenia/linie)
        // mieszka teraz w data-* na <ol id="verify-queue"> — widoczny nagłówek podsumowania nie istnieje.
        WebElement queueEl = wait.until(ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("verify-queue")));
        // Seed nie ma przyjętych partii → całą przestrzeń 1–10 pokrywa jedna karta luki.
        long rowCount = (Long) js.executeScript(
                "return document.querySelectorAll('#verify-queue .verify-row').length;");
        assertThat(rowCount).as("kolejka pokazuje cztery głosy AI + jedną kartę luki (1–10)").isEqualTo(5L);

        // Jedna karta na raz: tylko aktywna jest widoczna; reszta dostępna z paska u dołu.
        long activeCount = (Long) js.executeScript(
                "return document.querySelectorAll('#verify-queue .verify-row.is-active').length;");
        assertThat(activeCount).as("widoczna jest dokładnie jedna (aktywna) karta").isEqualTo(1L);
        String navPos = wait.until(d -> {
            String p = (String) js.executeScript("return document.getElementById('verify-nav-pos').textContent;");
            return p != null && p.length() > 0 ? p : null;
        });
        assertThat(navPos).as("licznik pozycji w kolejkach (4 głosy + karta luki)").isEqualTo("Karta 1 z 5");
        assertThat(queueEl.getAttribute("data-errors")).as("jeden błąd spójności (Klarnet 11–12 > 10 stron)")
                .isEqualTo("1");
        // Luka na s. 8 jest pewna; niezmapowane głosy zależą od reszty suity (band 1) — „co najmniej”.
        assertThat(Integer.parseInt(queueEl.getAttribute("data-warnings")))
                .as("co najmniej jedno ostrzeżenie (luka na s. 8)")
                .isGreaterThanOrEqualTo(1);
        // Błąd i ostrzeżenie są widoczne przy właściwych rolach.
        assertThat((Boolean) js.executeScript(
                "var r = Array.from(document.querySelectorAll('#verify-queue .verify-row'))"
                        + ".find(function (l) { var h=l.querySelector('.verify-role');"
                        + " return h && h.textContent.trim()==='Klarnet basowy';});"
                        + "return !!(r && r.querySelector('.issue-error'));"))
                .as("głos Klarnet basowy ma czerwony błąd").isEqualTo(true);
        assertThat((Boolean) js.executeScript(
                "var r = Array.from(document.querySelectorAll('#verify-queue .verify-row'))"
                        + ".find(function (l) { var h=l.querySelector('.verify-role');"
                        + " return h && h.textContent.trim()==='Saksofon altowy';});"
                        + "return !!(r && r.querySelector('.issue-warn'));"))
                .as("głos Saksofon altowy ma ostrzeżenie (luka)").isEqualTo(true);

        // Wiersz Z przypisanym instrumentem: atrybut data-instrument i pre-selekcja selectu
        // piastują gałąź „rozwiaż” szablonu (regresja: SpEL po nieistniejącej własności → biały ekran).
        long saxResolvedIdx = rowIndexByRole(js, "Saksofon altowy");
        assertThat(saxResolvedIdx).isGreaterThanOrEqualTo(0L);
        assertThat((String) js.executeScript(
                "return document.getElementById('accept-row-' + arguments[0]).getAttribute('data-instrument');",
                Long.valueOf(saxResolvedIdx)))
                .as("sugestia AI (rozwiązany instrument) jawnie w atrybucie data-instrument")
                .isEqualTo(String.valueOf(resolvedSaxInstrumentId));
        assertThat((String) js.executeScript(
                "return document.getElementById('fix-inst-' + arguments[0]).value;",
                Long.valueOf(saxResolvedIdx)))
                .as("select instrumentu pre-selekcjonuje sugestię AI")
                .isEqualTo(String.valueOf(resolvedSaxInstrumentId));
        // I wiersz BEZ przypisania: atrybut pusty lub nieobecny (gałąź null; Thymeleaf znosi
        // atrybut z pustą wartością).
        long trumpetNullIdx = rowIndexByRole(js, "Trąbka I");
        assertThat(trumpetNullIdx).isGreaterThanOrEqualTo(0L);
        String trumpetDataInst = (String) js.executeScript(
                "return document.getElementById('accept-row-' + arguments[0]).getAttribute('data-instrument');",
                Long.valueOf(trumpetNullIdx));
        assertThat(Objects.toString(trumpetDataInst, ""))
                .as("wiersz bez przypisanego instrumentu: pusty data-instrument").isEmpty();

        // ── Elementy wizualne kolejki (jeden rząd od/do; podwójny podgląd świadomie nie istnieje) ─
        // Każdy głos ma jeden rząd podglądu stron od/do z pliku nut (miniatury przez endpoint /thumb).
        boolean everyRowHasImage = (boolean) js.executeScript(
                "var ok = true;"
                        + "document.querySelectorAll('#verify-queue .verify-row').forEach(function (r) {"
                        + "  if (!r.querySelector('.verify-lp-page img')) { ok = false; }"
                        + "}); return ok;");
        assertThat(everyRowHasImage).as("każdy wiersz ma jeden podgląd stron (od/do z pliku nut)").isTrue();
        String saxImgSrc = (String) js.executeScript(
                "return document.querySelectorAll('#verify-queue .verify-row')[arguments[0]]"
                        + ".querySelector('.verify-lp-page img').src;",
                Long.valueOf(saxResolvedIdx));
        assertThat(saxImgSrc).as("miniatury pochodzą z pliku nut (endpoint /thumb)").contains("/thumb");
        String saxImgAlt = (String) js.executeScript(
                "return document.querySelectorAll('#verify-queue .verify-row')[arguments[0]]"
                        + ".querySelector('.verify-lp-page img').alt;",
                Long.valueOf(saxResolvedIdx));
        assertThat(saxImgAlt).as("alt miniatury identyfikuje stronę „od”").startsWith("Strona od:");

        // Tag / mapowanie głosu widoczne w karcie: rozwiązany wiersz → chip instrumentu.
        String saxChip = (String) js.executeScript(
                "var e = document.querySelectorAll('#verify-queue .verify-row')[arguments[0]].querySelector('.verify-instchip');"
                        + "return e ? e.textContent : '';",
                Long.valueOf(saxResolvedIdx));
        assertThat(saxChip).as("wiersz rozwiązany pokazuje chip z instrumentem w zespole").contains(resolvedSaxTag);
        boolean trumpetChipMissing = (boolean) js.executeScript(
                "return !!document.querySelectorAll('#verify-queue .verify-row')[arguments[0]].querySelector('.verify-tagmiss');",
                Long.valueOf(trumpetNullIdx));
        assertThat(trumpetChipMissing).as("wiersz bez tagu i mapowania sygnałizuje brak mapowania").isTrue();

        // Tabela instruments NIE jest truncowana między testami (patrz UiTestBase.cleanDatabase),
        // więc LIKE po nazwie kumuluje wiersze z innych metod — bierzemy WŁASNY instrument seedu.
        long instrumentId = resolvedSaxInstrumentId;

        // (2) Korekta zakresu „Trąbka I” 2–4 → 3–4 + jawny wybór instrumentu → HYBRID.
        long trumpetIdx = rowIndexByRole(js, "Trąbka I");
        assertThat(trumpetIdx).isGreaterThanOrEqualTo(0L);
        js.executeScript("document.getElementById('fix-from-' + arguments[0]).value = '3';", Long.valueOf(trumpetIdx));
        js.executeScript(
                "var s = document.getElementById('fix-inst-' + arguments[0]);"
                        + "s.value = arguments[1]; s.dispatchEvent(new Event('change', {bubbles: true}));",
                Long.valueOf(trumpetIdx), String.valueOf(instrumentId));
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById('accept-row-' + arguments[0]).click();", Long.valueOf(trumpetIdx));
        wait.until(d -> (Boolean) js.executeScript("""
                var li = Array.from(document.querySelectorAll('#verify-queue .verify-row'));
                return !!li[arguments[0]] && li[arguments[0]].classList.contains('is-accepted');""",
                Long.valueOf(trumpetIdx)));

        // Akceptacja skróconego zakresu (3–4) odsłania luki: s. 1–2 oraz 5–10 stają się kartami do oznaczenia;
        // licznik odzwierciedla powiększoną kolejkę (4 głosy AI + 2 luki = 6 kart).
        long gapCards = wait.until(d -> {
            Long g = (Long) js.executeScript(
                    "return document.querySelectorAll('#verify-queue .gap-card').length;");
            return g != null && g >= 1 ? g : 0L;
        });
        assertThat(gapCards).as("luki pojawiły się jako karty do oznaczenia").isGreaterThanOrEqualTo(1L);
        String navPos2 = (String) js.executeScript(
                "return document.getElementById('verify-nav-pos').textContent;");
        assertThat(navPos2).as("licznik po rozszerzeniu kolejki o luki").isEqualTo("Karta 2 z 6");

        // Po przyjęciu skróconego zakresu (AI 2–4 → 3–4, odsłonięta s. 2) „Następny"
        // przenosi na nowo powstałą kartę luki (s. 1–2), nie po prostu o krok dalej:
        // analogicznie w scenariuszu użytkownika 1–14 → 1–13 trafia tam s. 14 z podglądem.
        ((JavascriptExecutor) driver).executeScript("document.getElementById('verify-next-btn').click();");
        String navPosGap = wait.until(d -> {
            String p = (String) js.executeScript("return document.getElementById('verify-nav-pos').textContent;");
            return "Karta 5 z 6".equals(p) ? p : null;
        });
        assertThat(navPosGap).as("„Następny” po skróceniu zakresu wskazuje kartę luki").isEqualTo("Karta 5 z 6");
        boolean gapCardReady = (Boolean) js.executeScript(
                "var li=document.getElementById('verify-queue').children[4];"
                        + "return !!li"
                        + " && li.classList.contains('gap-card')"
                        + " && !!li.querySelector('select')"
                        + " && !!li.querySelector('.verify-lp-page img');");
        assertThat(gapCardReady).as("karta luki ma podgląd strony i wybór instrumentu (tagu)").isEqualTo(true);

        Map<String, Object> createdPart = jdbcTemplate.queryForList(
                "SELECT instrument_role, instrument_id, page_from, page_to, source FROM composition_instruments "
                        + "WHERE composition_id = ?", compositionId).stream()
                .filter(p -> "Trąbka I".equals(p.get("instrument_role"))).findFirst().orElseThrow();
        assertThat(String.valueOf(createdPart.get("page_from"))).isEqualTo("3");
        assertThat(String.valueOf(createdPart.get("page_to"))).isEqualTo("4");
        assertThat(createdPart.get("instrument_id")).isEqualTo(instrumentId);
        assertThat(String.valueOf(createdPart.get("source")))
                .as("korekta zakresu zostawia ślad HYBRID (audyt: AI=2–4, ostatecznie 3–4)")
                .isEqualTo("HYBRID");

        // (3) Masowe „pozostałe bez zmian”: Klarnet 11–12 poza nutami → całe żądanie odrzucone
        // (@Transactional → zero częściowego zapisu). Hook na window.fetch daje deterministyczny
        // sygnał „odpowiedź odebrana” (nie racyjny stan przycisku).
        long beforeBulk = partCount();
        assertThat(beforeBulk).isEqualTo(1L);
        js.executeScript("""
                window.__bulkSettled = false;
                if (!window.__fetchHooked) {
                    var origFetch = window.fetch.bind(window);
                    window.fetch = function (url, opts) {
                        var p = origFetch(url, opts);
                        try {
                            if (String(url).indexOf('accept-corrected') >= 0
                                    && String((opts && opts.method) || 'GET').toUpperCase() === 'POST') {
                                p.then(function () { window.__bulkSettled = true; },
                                       function () { window.__bulkSettled = true; });
                            }
                        } catch (e) { /* flaga pozostaje false — nie psujemy żądania */ }
                        return p;
                    };
                    window.__fetchHooked = true;
                }
            """);
        org.openqa.selenium.WebElement bulkBtn = wait.until(ExpectedConditions.elementToBeClickable(
                org.openqa.selenium.By.id("accept-unchanged-btn")));
        bulkBtn.click();
        wait.until(d -> Boolean.TRUE.equals(js.executeScript("return window.__bulkSettled === true;")));
        awaitPageSettled();
        assertThat(partCount()).as("rollback: ani jeden głos z partii masowej nie wszedł do bazy")
                .isEqualTo(beforeBulk);

        // (4) Głosy poprawne przyjęte po kolei z jawnym instrumentem — licznik rośnie dokładnie o dwa.
        acceptRowByRoleOrSkip(js, "Tuba E", instrumentId);
        long tubaIdx = rowIndexByRole(js, "Tuba E");
        wait.until(d -> (Boolean) js.executeScript("""
                var li = Array.from(document.querySelectorAll('#verify-queue .verify-row'));
                return !!li[arguments[0]] && li[arguments[0]].classList.contains('is-accepted');""", Long.valueOf(tubaIdx)));

        acceptRowByRoleOrSkip(js, "Saksofon altowy", instrumentId);
        long saxIdx = rowIndexByRole(js, "Saksofon altowy");
        wait.until(d -> (Boolean) js.executeScript("""
                var li = Array.from(document.querySelectorAll('#verify-queue .verify-row'));
                return !!li[arguments[0]] && li[arguments[0]].classList.contains('is-accepted');""", Long.valueOf(saxIdx)));

        assertThat(partCount()).as("łącznie trzy zaakceptowane partie (Trąbka skorygowana + Tuba + Saksofon)")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("skrócenie zakresu JUŻ PRZYJĘTEGO głosu → aktualizacja partii, nie 400; strona luki do oznaczenia")
    void correctionOfAlreadyAcceptedVoiceUpdatesPart() throws Exception {
        // Regresja prod 2026-11-21 („Amerland”: AI wyznaczyła „Partytura” na 1–14, user skracając
        // do 1–13 dostawał 400 „nachodzi na partię str. 1–14 („Partytura”)” — własna partia
        // blokowała własną korektę). Analogicznie tu: Tuba E już zaakceptowana na 5–7.
        seedSucceededAnalysis();
        jdbcTemplate.update("""
                INSERT INTO composition_instruments
                    (composition_id, instrument_id, instrument_role, page_from, page_to,
                     score_file_id, source, confidence_score, verified_by, verified_at,
                     created_at, updated_at)
                VALUES (?, ?, 'Tuba E', 5, 7, ?, 'AI', 0.85, 'seed', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
                compositionId, resolvedSaxInstrumentId, scoreFileId);

        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        WebElement entry = wait.until(
                ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("ai-verify-entry")));
        entry.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("verify-queue")));

        long tubaIdx = rowIndexByRole(js, "Tuba E");
        assertThat(tubaIdx).isGreaterThanOrEqualTo(0L);
        long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name LIKE 'Klarnety raportowe%'",
                Long.class);

        // Korekta zakresu 5–7 → 5–6 + jawny wybór instrumentu → akceptacja = aktualizacja partii.
        js.executeScript("document.getElementById('fix-to-' + arguments[0]).value = '6';",
                Long.valueOf(tubaIdx));
        js.executeScript(
                "var s = document.getElementById('fix-inst-' + arguments[0]);"
                        + "s.value = arguments[1]; s.dispatchEvent(new Event('change', {bubbles: true}));",
                Long.valueOf(tubaIdx), String.valueOf(instrumentId));
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById('accept-row-' + arguments[0]).click();", Long.valueOf(tubaIdx));
        wait.until(d -> (Boolean) js.executeScript("""
                var li = Array.from(document.querySelectorAll('#verify-queue .verify-row'));
                return !!li[arguments[0]] && li[arguments[0]].classList.contains('is-accepted');""",
                Long.valueOf(tubaIdx)));

        assertThat(partCount()).as("korekta = aktualizacja istniejącej partii, zero nowych wierszy")
                .isEqualTo(1L);
        Map<String, Object> tuba = jdbcTemplate.queryForList(
                        "SELECT instrument_role, page_from, page_to FROM composition_instruments "
                                + "WHERE composition_id = ?",
                        compositionId).stream()
                .filter(p -> "Tuba E".equals(p.get("instrument_role"))).findFirst().orElseThrow();
        assertThat(String.valueOf(tuba.get("page_from"))).isEqualTo("5");
        assertThat(String.valueOf(tuba.get("page_to")))
                .as("zakres skorygowany 5–7 → 5–6 (przed naprawą: HTTP 400 „nachodzi na partię ...”)")
                .isEqualTo("6");

        // Strona 7 — zwolniona przez skrócony zakres — stała się kartą luki do oznaczenia.
        wait.until(d -> {
            Long g = (Long) js.executeScript(
                    "return document.querySelectorAll('#verify-queue .gap-card').length;");
            return g != null && g >= 1 ? g : 0L;
        });
        boolean gapCoversPage7 = (Boolean) js.executeScript("""
                return Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .some(function (g) { return g.textContent.indexOf('7') >= 0; });""");
        assertThat(gapCoversPage7).as("strona 7 pojawiła się jako karta luki").isEqualTo(true);
    }

    @Test
    @DisplayName("oznaczenie luki „nie używam”: karta z przypinką + audyt w bazie; Przywróć przywraca lukę i czyści bazę")
    void unusedPageMarkingRoundtrip() throws Exception {
        seedSucceededAnalysis();
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        WebElement entry = wait.until(
                ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("ai-verify-entry")));
        entry.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(org.openqa.selenium.By.id("verify-queue")));

        // Seed nie ma przyjętych partii → cała przestrzeń 1–10 jest jedną kartą luki.
        boolean gapCardPresent = (Boolean) js.executeScript("""
                var g = Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .find(function (g) { return g.dataset.gapFrom === '1' && g.dataset.gapTo === '10'; });
                return !!g;""");
        assertThat(gapCardPresent).as("niepokryte strony renderują się jako karta luki już na starcie").isTrue();

        // (1) „Nie używam tych stron” → POST /pages/exclude: karta dostaje przypinkę, audyt leci do bazy.
        js.executeScript("""
                var g = Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .find(function (g) { return g.dataset.gapFrom === '1' && g.dataset.gapTo === '10'; });
                g.querySelector('.gap-unused-btn').click();""");
        wait.until(d -> (Boolean) js.executeScript("""
                var g = Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .find(function (g) { return g.dataset.gapFrom === '1' && g.dataset.gapTo === '10'; });
                return !!g && g.classList.contains('gap-excluded')
                        && !!g.querySelector('.verify-exclbadge');"""));
        Map<String, Object> exclusion = jdbcTemplate.queryForList("""
                SELECT page_from, page_to, marked_by FROM score_page_exclusions
                WHERE composition_id = ? AND score_file_id = ?""", compositionId, scoreFileId)
                .stream().findFirst().orElseThrow();
        assertThat(String.valueOf(exclusion.get("page_from"))).isEqualTo("1");
        assertThat(String.valueOf(exclusion.get("page_to"))).isEqualTo("10");
        assertThat(Objects.toString(exclusion.get("marked_by")))
                .as("oznaczenie niesie parę audytową (autor)").isNotEmpty();

        // (2) „Przywróć” → POST /pages/restore: znaczenie znika i z karty, i z bazy (odwracalność).
        js.executeScript("""
                var g = Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .find(function (g) { return g.dataset.gapFrom === '1' && g.dataset.gapTo === '10'; });
                g.querySelector('.gap-restore-btn').click();""");
        wait.until(d -> (Boolean) js.executeScript("""
                var g = Array.from(document.querySelectorAll('#verify-queue .gap-card'))
                        .find(function (g) { return g.dataset.gapFrom === '1' && g.dataset.gapTo === '10'; });
                return !!g && !g.classList.contains('gap-excluded')
                        && !!g.querySelector('.gap-unused-btn');"""));
        long marksLeft = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM score_page_exclusions WHERE composition_id = ? AND score_file_id = ?""",
                Long.class, compositionId, scoreFileId);
        assertThat(marksLeft).as("przywrócenie usuwa oznaczenie z bazy (działanie w pełni odwracalne)").isEqualTo(0L);
    }

    @Test
    @DisplayName("bez zakończonej analizy: brak linku wejściowego; raport renderuje stan pusty")
    void emptyStateWithoutAnalysis() throws Exception {
        seedCompositionWithoutAnalysis();
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        JavascriptExecutor js = (JavascriptExecutor) driver;
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        wait.until(ExpectedConditions.invisibilityOfElementLocated(org.openqa.selenium.By.id("ai-verify-entry")));
        // Nawet przy bezpośrednim adresie strona musi dać stan pusty, nie 500.
        driver.get(baseUrl() + "/bands/1/compositions/" + compositionId + "/verify-ai");
        WebElement empty = wait.until(ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.cssSelector(".verify-empty")));
        assertThat(empty.getText()).contains("Brak gotowej propozycji AI");
    }

    private void awaitPageSettled() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void acceptRowByRoleOrSkip(JavascriptExecutor js, String role, long instrumentId) {
        long idx = rowIndexByRole(js, role);
        if (idx < 0) {
            return;
        }
        js.executeScript(
                "var s = document.getElementById('fix-inst-' + arguments[1]);"
                        + "if (s) { s.value = String(arguments[0]); s.dispatchEvent(new Event('change', {bubbles: true})); }",
                instrumentId, Long.valueOf(idx));
        js.executeScript("document.getElementById('accept-row-' + arguments[0]).click();", Long.valueOf(idx));
    }
}
