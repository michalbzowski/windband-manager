package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mobile (zgłoszenie po #321/#323): po wpisaniu filtra na liście członków widok "przewijał się
 * na sam dół" i filtr znikał spod oczu. Kontrakt: po wymianie #member-list-region widok zostaje
 * tam, gdzie był w chwili wysłania żądania, a pole wyszukiwania pozostaje w polu widzenia —
 * niezależnie od tego, co po swapie rusza przewinięciem (docisk do krótszej strony, animacje
 * smooth-scrolla z zapisu/edycji, korekta viewportu pod klawiaturą).
 *
 * <p>Scenariusze:</p>
 * <ul>
 *   <li>C — czysty filtr: przewinięta lista, wpis do filtru, wymiana rejonu. Widok nie może
 *       uciec poniżej punktu wysłania, a input musi pozostać w polu widzenia.</li>
 *   <li>B — "scenariusz z zgłoszenia": świadome przewinięcie do osoby po zapisie/edycji
 *       (initFocusHighlight + scrollIntoView smooth) JESZCZE W TOKU, a użytkownik wpisuje
 *       filtr. Kotwica scrolla (configRequest + afterSettle nad #member-list-region) musi wygrać
 *       z animacją — widok wyląduje tam, gdzie był przy wysyłce, nie tam, dokąd ciągnie smooth.</li>
 * </ul>
 *
 * <p>Pisanie filtru wykonujemy zdefiniowanym zdarzeniem 'input' (ustawiamy wartość + dispatch) —
 * htmx odpala dokładnie to samo żądanie, co fizyczne pisanie, ale bez zależnosci od pozycji
 * kursora/webdrivera w trakcie ruchomego przewinięcia (realne typowanie na mobile robi to samo).</p>
 *
 * <p>Brak emulacji CDP sieci (runner CI potrafi ją po cichu ignorować). Asercje inwaryjne wobec
 * zawartości listy (tolerancja na pozostałości H2 z równoległych klas, nie twarde liczniki).</p>
 */
class MemberListMobileScrollStabilityUiTest extends UiTestBase {

    private String marker;

    @Test
    void mobileFiltering_neverLeavesScrollBarBelowRequestPoint_orHidesInput() throws Exception {
        seedLongList();
        loginAndNavigateTo("/members");
        new WebDriverWait(driver, Duration.ofSeconds(30))
                .until(d -> !d.findElements(By.cssSelector("#member-filters input[name='q']")).isEmpty());
        ensurePageReady();

        overrideMobileViewport(390, 844);
        try {
            // Lista MUSI być przewijalna w mobilnym viewporcie — inaczej test bezwartościowy.
            assertThat(scrollMax()).as("lista dłuższa niż mobilny viewport (seed gwarantuje)")
                    .isGreaterThanOrEqualTo(300);

            installDiagnostics();

            // ---- C. Czysty filtr: przewijam 600 px w dół i wpisuję unikalny prefiks (1 linia).
            // Wymiana regionu nie może zrzucić widoku poniżej punktu wysyłki ani ukryć filtra.
            int scrollC = jsInt(
                    "window.scrollTo(0, Math.min(600, document.documentElement.scrollHeight - window.innerHeight));" +
                            "return Math.round(window.scrollY);");
            assertThat(scrollC).as("przewinięcie startowe scenariusza C faktycznie > 0").isGreaterThan(0);

            typeFilter(marker); // pełny marker występuje TYLKO w mojej linii Nowak

            waitScrollSettled();
            diagState("po C");
            int yC = jsInt("return Math.round(window.scrollY);");
            assertThat(yC).as("C: widok nie może uciec poniżej punktu wysyłki (było %d)", scrollC)
                    .isLessThanOrEqualTo(scrollC + 12);

            assertInputVisibleWith(marker);
            assertLoaderNotStuck();

            // ---- B. Pre-save/edycja momentum: świadome smooth przewinięcie do głębokiego wiersza,
            // a W PODCZAS jego animacji — filtr pasujący do dużej części listy. Kotwica musi
            // wygrać z animacją: pozycja końcowa nie może pójść poniżej momentu wysyłki żądania.
            // (Po scenariuszu C lista ma jedną linię — najpierw przywracam pełną.)
            typeFilter("");
            waitScrollSettled();
            int rowsAfterReset = jsInt(
                    "return document.getElementById('member-list-region').querySelectorAll('tr[data-member-id]').length;");
            assertThat(rowsAfterReset).as("B: pełny list przywrócony przed scenariuszem B").isGreaterThanOrEqualTo(5);

            // Cel: głęboki wiersz "Filip", który przetrwa filtr 'Filipowska' — przewinięcie ma
            // żywy cel i realnie konkuruje z kotwicą.
            String deepRowId = jsString(
                    "var rows = Array.prototype.slice.call(document.querySelectorAll('#member-list-region tr[data-member-id]'));" +
                            "for (var i = rows.length - 1; i >= 0; i--) {" +
                            "var cell = rows[i].querySelector('td');" +
                            "if (cell && /^Filip/.test((cell.textContent || '').trim())) { return rows[i].getAttribute('data-member-id'); }" +
                            "}" +
                            "return '';");
            assertThat(deepRowId).as("B: głęboki wiersz 'Filip' jako cel przewinięcia").isNotBlank();

            String rampDiag = jsString(
                    "var row = document.getElementById('member-' + arguments[0]);" +
                            "if (!row) { return 'NO-ROW'; }" +
                            "var r = row.getBoundingClientRect();" +
                            "return JSON.stringify({rectTop: Math.round(r.top), rectH: Math.round(r.height)," +
                                    "scrollY: Math.round(window.scrollY || 0), innerH: window.innerHeight," +
                                    "pageMax: Math.max(0, document.documentElement.scrollHeight - window.innerHeight)});", deepRowId);
            System.out.println("DIAG[rampDiag] " + rampDiag);

            // Symulacja "momentum po zapisie/edycji": pętla rAF sunąca w stałym tempie do głębokiego
            // wiersza (w headless + emulacji CDP scrollIntoView({behavior:'smooth'}) nie animuje
            // deterministycznie). Dla strony to ten sam rodzaj przewijania programowego w toku,
            // które kotwica musi zatrzymać — a dla testu tempo i koniec są odtwarzalne.
            Object rampStart = ((JavascriptExecutor) driver).executeScript(
                    "var row = document.getElementById('member-' + arguments[0]);" +
                            "if (!row) { return 'NO-ROW'; }" +
                            "var r = row.getBoundingClientRect();" +
                            "var targetY = (window.scrollY || 0) + r.top - (window.innerHeight / 2) + (r.height / 2);" +
                            "targetY = Math.max(0, Math.min(targetY, Math.max(0, document.documentElement.scrollHeight - window.innerHeight)));" +
                            "if ((window.scrollY || 0) + 300 > targetY) { return 'TOO-CLOSE:' + Math.round(targetY) + ':' + Math.round(window.scrollY || 0); }" +
                            "window.__rampActive = true;" +
                            "(function step() {" +
                            "if (!window.__rampActive) { return; }" +
                            "if (Date.now() - (step.t0 ||= Date.now()) > 1500) { window.__rampActive = false; return; }" +
                            "try { window.scrollTo(0, Math.min(targetY, (window.scrollY || 0) + 45)); } catch (_e) {}" +
                            "if ((window.scrollY || 0) >= targetY - 1) { window.__rampActive = false; return; }" +
                            "requestAnimationFrame(step);" +
                            "})();" +
                            "return 'OK';", deepRowId);
            System.out.println("DIAG[ramp] " + rampStart);
            assertThat(rampStart).as("B: przewinięcie w toku wystartowało (głęboki cel, ≥ 300 px dalej)").isEqualTo("OK");

            // Oczekiwanie (polling), aż przewinięcie faktycznie ruszy — deterministycznie.
            long animDeadline = System.currentTimeMillis() + 1500;
            int yMid = 0;
            while (System.currentTimeMillis() < animDeadline) {
                yMid = jsInt("return Math.round(window.scrollY);");
                if (yMid > 40) { break; }
                sleepQuietly(50);
            }
            assertThat(yMid).as("B: przewinięcie w toku (początek > 40 px)").isGreaterThan(40);

            typeFilter("Filipowska"); // 24+ linie — wysoka lista po wymianie, scroll dalej możliwy

            waitScrollSettled();
            diagState("po B");
            int yB = jsInt("return Math.round(window.scrollY);");
            assertThat(yB).as("B: widok nie może uciec poniżej punktu wysyłki (było %d)", yMid)
                    .isLessThanOrEqualTo(yMid + 12);

            assertInputVisibleWith("Filipowska");
            assertLoaderNotStuck();
        } finally {
            clearViewportOverride();
        }
    }

    // ---- seed ---------------------------------------------------------------------------

    /** Jan Nowak <unikalny marker> (jedyna linia pasująca do niego) + 24 aktywne "Filipowska" —
     * lista wyraźnie dłuższa niż mobilny viewport, więc scroll i docisk są realne. */
    private void seedLongList() {
        marker = UUID.randomUUID().toString().substring(0, 8);
        createTestBand1Member("Jan", "Nowak " + marker, null);
        for (int i = 0; i < 24; i++) {
            jdbcTemplate.update(
                    "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                            "VALUES (?, ?, '199" + (i % 10) + "-01-0" + (i % 9 + 1) + "', true, CURRENT_DATE, false, 1)",
                    "Filip " + i, "Filipowska");
        }
    }

    @AfterEach
    void cleanup() {
        if (marker != null) {
            try {
                jdbcTemplate.update("DELETE FROM members WHERE last_name LIKE ? OR last_name = ?",
                        "%" + marker + "%", "Filipowska");
            } catch (RuntimeException ignoredCleanup) {
                // baza testowa i tak zostaje resetowana (UiTestBase) — nie blokujemy wyniku testu
            }
        }
        try {
            clearViewportOverride();
        } catch (RuntimeException emulationGone) {
            // driver PER_CLASS: emulacja znika przy najbliższej nawigacji
        }
    }

    // ---- narzędzia ------------------------------------------------------------------------

    /** Wirtualna klawiatura: wartość pola + native event 'input'/'change' — htmx wysyła dokładnie
     * to samo żądanie, co fizyczne pisanie, bez zależności od aktualnej pozycji elementu
     * (scenariusz z animacją w toku; realne typowanie na mobile robi dokładnie to samo). */
    private void typeFilter(String value) {
        ((JavascriptExecutor) driver).executeScript(
                "var i = document.querySelector('#member-filters input[name=q]');" +
                        "i.value = arguments[0];" +
                        "i.dispatchEvent(new Event('input', {bubbles: true}));" +
                        "i.dispatchEvent(new Event('change', {bubbles: true}));", value);
    }

    private void assertInputVisibleWith(String expectedValue) {
        String state = jsString(
                "var i = document.querySelector('#member-filters input[name=q]');" +
                        "if (!i) { return 'brak-input'; }" +
                        "var r = i.getBoundingClientRect();" +
                        "var h = document.documentElement.clientHeight;" +
                        "return i.value + '|' + Math.round(r.top) + '|' + Math.round(r.bottom) + '|' + h;");
        assertThat(state).as("input z filtraitu istnieje").isNotEqualTo("brak-input");
        String[] parts = state.split("\\|");
        assertThat(parts[0]).as("wartość filtra przetrwała wymianę rejonu").isEqualTo(expectedValue);
        double top = Double.parseDouble(parts[1]);
        double bottom = Double.parseDouble(parts[2]);
        int clientHeight = Integer.parseInt(parts[3]);
        assertThat(top).as("filtr: górna krawędź w polu widzenia").isGreaterThanOrEqualTo(-2);
        assertThat(bottom).as("filtr: dolna krawędź w polu widzeniu").isLessThanOrEqualTo(clientHeight + 2);
    }

    private void assertLoaderNotStuck() {
        Object free = ((JavascriptExecutor) driver).executeScript(
                "var el = document.getElementById('member-list-loading');" +
                        "return !el || !el.classList.contains('htmx-request');");
        assertThat(Boolean.TRUE.equals(free))
                .as("wskaźnik bez pozostawionej klasy htmx-request (niezawieszony)")
                .isTrue();
    }

    private void installDiagnostics() {
        ((JavascriptExecutor) driver).executeScript(
                "window.__diagEvents = [];" +
                        "['htmx:configRequest','htmx:beforeSend','htmx:responseHandle','htmx:beforeSwap'," +
                        "'htmx:afterSwap','htmx:afterSettle','htmx:sendError','htmx:responseError','htmx:swapError']" +
                        ".forEach(function (n) {" +
                        "document.addEventListener(n, function (e) {" +
                        "try { var d = e.detail || {};" +
                        "window.__diagEvents.push(n + ':' + (d.requestConfig ? d.requestConfig.url.split('/').pop() : '-'));" +
                        "} catch (_x) { window.__diagEvents.push(n); }" +
                        "});});");
    }

    private void diagState(String context) {
        System.out.println("DIAG[" + context + "] events=" + jsString(
                "return JSON.stringify(window.__diagEvents || []);")
                + " regionRows=" + jsString(
                "var r = document.getElementById('member-list-region');" +
                        "return r ? r.querySelectorAll('tr[data-member-id]').length : -1;"));
    }

    /** Czekamy, aż scrollY ustabilizuje się NA DŁUŻEJ niż okno ochronne kotwicy (3 s) nie musi —
     * wystarczy 600 ms bez zmian. Okno kotwicy i tak aktywnie broni pozycji. */
    private void waitScrollSettled() {
        int last = -1;
        long stableSince = System.currentTimeMillis();
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            int cur = jsInt("return Math.round(window.scrollY);");
            if (cur != last) {
                last = cur;
                stableSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stableSince >= 600) {
                return;
            }
            sleepQuietly(80);
        }
    }

    private void ensurePageReady() {
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> Boolean.TRUE.equals(
                ((JavascriptExecutor) d).executeScript(
                        "return document.readyState === 'interactive' || document.readyState === 'complete';")));
        assertThat(((JavascriptExecutor) driver).executeScript("return typeof window.htmx;"))
                .as("htmx zainicjowany").isEqualTo("object");
    }

    private int jsInt(String script, Object... args) {
        Number n = (Number) ((JavascriptExecutor) driver).executeScript(script, args);
        return n != null ? n.intValue() : 0;
    }

    private String jsString(String script, Object... args) {
        return String.valueOf(((JavascriptExecutor) driver).executeScript(script, args));
    }

    private int scrollMax() {
        return jsInt("return Math.max(0, document.documentElement.scrollHeight - window.innerHeight);");
    }

    private void overrideMobileViewport(int width, int height) {
        ((org.openqa.selenium.chromium.HasCdp) driver).executeCdpCommand(
                "Emulation.setDeviceMetricsOverride", Map.of(
                        "width", width,
                        "height", height,
                        "deviceScaleFactor", 2,
                        "mobile", true));
    }

    private void clearViewportOverride() {
        ((org.openqa.selenium.chromium.HasCdp) driver)
                .executeCdpCommand("Emulation.clearDeviceMetricsOverride", Map.of());
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
