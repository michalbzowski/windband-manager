package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ładowanie filtrowanych członków (wyszukiwarka, przełącznik Aktywni/Nieaktywni) szło po sieci,
 * ale przez ten czas nic na ekranie się nie działo — użytkownik nie wiedział, czy klik
 * zadziałał. Pasek filtrów ma teraz wskaźnik #member-list-loading pokazany przez HTMX w parze
 * z klasą htmx-request (mechanizm hx-indicator — ten sam wzorzec co #register-spinner),
 * i znika gdy żądanie się zakończy.
 *
 * <p>Okno "w locie" jest tu deterministyczne: test włącza emulację opóźnienia sieciowego przez
 * CDP (Network.emulateNetworkConditions), więc pokazanie się wskaźnika da się obserwować
 * pollingiem, bez wyścigu z błyskawiczną lokalną odpowiedzią. Emulacja jest wyłączana zawsze —
 * także w razie błędu testu (@AfterEach).</p>
 */
class MemberListLoadingIndicatorUiTest extends UiTestBase {

    private String marker;
    private boolean latencyEnabled = false;

    @Test
    void loaderShowsDuringFilterRequestsAndToggle_andClearsWhenSettled() throws Exception {
        seedBand();
        enableSlowNetwork(900); // ms dodatkowego opóźnienia na żądanie — okno na obserwację wskaźnika
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> !d.findElements(By.cssSelector("#member-filters input[name='q']")).isEmpty());
        // Bramka antywyścigowa: pole może stać w DOM PRZED tym, jak htmx się zainicjalizuje
        // (skrypt blokuje parse, ale inits się przy DOMContentLoaded). Pod obciążeniem
        // full-suite okno bywa szersze — czekamy na gotowość dokumentu, a nie tylko na element.
        ensurePageReady();

        // ---- 0. Okablowanie: wszystkie trzy źródła requestów wskazują ten sam wskaźnik ----
        assertThat(indicatorOf("#member-filters input[name='q']"))
                .as("wyszukiwarka ma hx-indicator").isEqualTo("#member-list-loading");
        assertThat(indicatorOf("#member-filters select[name='instrument']"))
                .as("filtr sekcji ma hx-indicator").isEqualTo("#member-list-loading");
        assertThat(indicatorOf(".member-status-toggle"))
                .as("segment Aktywni/Nieaktywni ma hx-indicator").isEqualTo("#member-list-loading");

        WebElement loading = driver.findElement(By.cssSelector("#member-list-loading"));
        assertThat(loading.getAttribute("role")).as("wskaźnik to role=status").isEqualTo("status");
        assertThat(loaderHidden()).as("wskaźnik jest początkowo ukryty").isTrue();

        // ---- 1. Wyszukiwarka: wskaźnik widoczny podczas żądania, znika po wymianie fragmentu ----
        // Pod pełnym obciążeniem równoległym pierwsze wpisanie bywa "zgubione" (stany in-flight /
        // zatknięty dispatch zdarzeń Chrome'a). Retry z RESETEM: odświeżenie strony daje świeży
        // element i czyści stan żądań HTMXa, więc druga próba zaczyna od czystego stanu.
        typeSearchWithRetry("K");

        // Gdy żądanie się zamknie, wskaźnik musi zniknąć i lista faktycznie zostać odfiltrowana.
        waitForLoaderHidden("po zakończeniu żądania wyszukiwania");
        // Weryfikacja na ŻYWYCH węzłach wewnątrz JavaScriptu: snapshot Seleniumowy
        // (findElements + getText) potrafi umrzeć w środku wymiany HTMXa (StaleElement),
        // a tekst odczytany z odświeżonego fragmentu to jedyny wiarygodny stan "po swapie".
        new FluentWait<>(driver)
                .pollingEvery(Duration.ofMillis(20))
                .withTimeout(Duration.ofSeconds(10))
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "var cells = document.querySelectorAll('#members-table tbody tr td');" +
                        "for (var i = 0; i < cells.length; i++) {" +
                        "  var t = cells[i].textContent || '';" +
                        "  if (t.indexOf(arguments[0]) >= 0) { return true; }" +
                        "}" +
                        "return false;", marker)));
        assertLoaderNotStuck();

        // ---- 2. Segment Aktywni/Nieaktywni: to samo zachowanie przy przełączaniu ----
        clickElement(By.id("toggleMembersBtn"));
        waitForLoaderVisible("po kliknięciu segmentu Nieaktywni");
        wait.until(d -> "true".equals(((JavascriptExecutor) d).executeScript(
                "return document.getElementById('members-content').getAttribute('data-show-inactive');")));
        waitForLoaderHidden("po wymianie widoku nieaktywnych");
        assertLoaderNotStuck();

        // ---- 3. Powrót do aktywnych — zamknięcie cyklu, brak pozostawionego wskaźnika ----
        clickElement(By.id("toggleMembersBtn"));
        wait.until(d -> "false".equals(((JavascriptExecutor) d).executeScript(
                "return document.getElementById('members-content').getAttribute('data-show-inactive');")));
        waitForLoaderHidden("po powrocie do widoku aktywnych");
        assertLoaderNotStuck();
    }

    // ---- sprzątanie / narzędzia testowe -------------------------------------------------

    @AfterEach
    void releaseNetworkConditions() {
        disableSlowNetwork();
    }

    private void enableSlowNetwork(int latencyMs) throws Exception {
        if (latencyEnabled) {
            return;
        }
        ((org.openqa.selenium.chromium.HasCdp) driver).executeCdpCommand(
                "Network.emulateNetworkConditions", Map.of(
                        "offline", false,
                        "latency", latencyMs,
                        "downloadThroughput", -1,
                        "uploadThroughput", -1));
        latencyEnabled = true;
    }

    private void disableSlowNetwork() {
        if (!latencyEnabled) {
            return;
        }
        try {
            ((org.openqa.selenium.chromium.HasCdp) driver).executeCdpCommand(
                    "Network.emulateNetworkConditions", Map.of(
                            "offline", false,
                            "latency", 0,
                            "downloadThroughput", -1,
                            "uploadThroughput", -1));
        } catch (RuntimeException e) {
            // Nawet gdyby CDP nie przyjęło resetu — kolejne testy i tak zaczynają od pełnej nawigacji.
        }
        latencyEnabled = false;
    }

    private String indicatorOf(String selector) {
        return driver.findElement(By.cssSelector(selector)).getAttribute("hx-indicator");
    }

    private boolean loaderHidden() {
        try {
            Object value = ((JavascriptExecutor) driver).executeScript(
                    "var el = document.querySelector('#member-list-loading');" +
                    "return el && getComputedStyle(el).display === 'none';");
            return Boolean.TRUE.equals(value);
        } catch (StaleElementReferenceException swappedAway) {
            return false; // środek wymiany — polling powtórzy sprawdzanie
        }
    }

    /** Bramka gotowości: dokument załadowany + htmx zainicjowany (związane listeners). */
    private void ensurePageReady() {
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> Boolean.TRUE.equals(
                ((JavascriptExecutor) d).executeScript(
                        "return document.readyState === 'interactive' || document.readyState === 'complete';")));
        assertThat(((JavascriptExecutor) driver).executeScript("return typeof window.htmx;"))
                .as("htmx zainicjowany").isEqualTo("object");
    }

    /**
     * Wpisanie litery w wyszukiwarkę z retry pod obciążeniem. Każda próba startuje od pewnego
     * stanu: wskaźnik ukryty i htmx gotowy; jeśli wpisanie nie wywołało widocznego żądania,
     * odświeżamy stronę (świeży element + czysty stan HTMXa) i próbujemy raz jeszcze.
     * Kliknięcie i pisanie są stale-tolerancyjne — pod obciążeniem dokument bywa w trakcie
     * swapu/navigation, więc element prze-rodzimy w każdej iteracji.
     */
    private void typeSearchWithRetry(String text) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            ensurePageReady();
            if (!probeLoaderHidden(3)) {
                // stan in-flight z wcześniejszej próby — reset przez reload, a nie pisanie w bok
                driver.navigate().refresh();
                continue;
            }
            try {
                clickElement(By.cssSelector("#member-filters input[name='q']"));
                sendKeysInto(By.cssSelector("#member-filters input[name='q']"), text);
                waitForLoaderVisible("po literze w wyszukiwarce");
                return;
            } catch (org.openqa.selenium.TimeoutException firstAttemptLost) {
                // zdarzenie mogło dojść, zanim htmx zareagował — reset i powtórka
                driver.navigate().refresh();
                if (attempt == 2) {
                    throw firstAttemptLost;
                }
            }
        }
    }

    /** Klik z ponownym wyborem elementu przy StaleElement (swap/navigation pod obciążeniem). */
    private void clickElement(By locator) {
        StaleElementReferenceException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                driver.findElement(locator).click();
                return;
            } catch (StaleElementReferenceException stale) {
                last = stale;
                sleepQuietly(400);
            }
        }
        throw last;
    }

    /** To samo dla Actions.sendKeys — pisanie w "środku swapu" daje stale, więc powtarzamy. */
    private void sendKeysInto(By locator, String text) {
        StaleElementReferenceException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                new Actions(driver).moveToElement(driver.findElement(locator))
                        .click().sendKeys(text).perform();
                return;
            } catch (StaleElementReferenceException stale) {
                last = stale;
                sleepQuietly(400);
            }
        }
        throw last;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Nie-rzucający wariant: true, gdy wskaźnik zniknie przed timeoutem (w ms). */
    private boolean probeLoaderHidden(long seconds) {
        try {
            new FluentWait<>(driver)
                    .ignoring(StaleElementReferenceException.class)
                    .pollingEvery(Duration.ofMillis(100))
                    .withTimeout(Duration.ofSeconds(seconds))
                    .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                            "var el = document.querySelector('#member-list-loading');" +
                            "return el && getComputedStyle(el).display === 'none';")));
            return true;
        } catch (org.openqa.selenium.TimeoutException stillInFlight) {
            return false;
        }
    }

    private void waitForLoaderVisible(String context) {
        new FluentWait<>(driver)
                .ignoring(StaleElementReferenceException.class)
                .withMessage("wskaźnik ładowania nie pojawił się " + context)
                .pollingEvery(Duration.ofMillis(10))
                .withTimeout(Duration.ofSeconds(15))
                .until(d -> Boolean.TRUE.equals(
                        ((JavascriptExecutor) d).executeScript(
                                "var el = document.querySelector('#member-list-loading');" +
                                "return el && getComputedStyle(el).display !== 'none';")));
    }

    private void waitForLoaderHidden(String context) {
        new FluentWait<>(driver)
                .ignoring(StaleElementReferenceException.class)
                .withMessage("wskaźnik ładowania nie zniknął " + context)
                .pollingEvery(Duration.ofMillis(10))
                .withTimeout(Duration.ofSeconds(15))
                .until(d -> Boolean.TRUE.equals(
                        ((JavascriptExecutor) d).executeScript(
                                "var el = document.querySelector('#member-list-loading');" +
                                "return el && getComputedStyle(el).display === 'none';")));
    }

    /** Brak "zawieszonego" wskaźnika: klasa htmx-request musi zostać zdjęta po każdym żądaniu. */
    private void assertLoaderNotStuck() {
        Object stuck = ((JavascriptExecutor) driver).executeScript(
                "var el = document.querySelector('#member-list-loading');" +
                "return !el || !el.classList.contains('htmx-request');");
        assertThat(Boolean.TRUE.equals(stuck))
                .as("wskaźnik bez pozostawionej klasy htmx-request (czyli na pewno ukryty)")
                .isTrue();
    }

    /** Jedno aktywne tło + jeden nieaktywny — każde po swojej stronie przełącznika. */
    private void seedBand() {
        marker = UUID.randomUUID().toString().substring(0, 8);
        createTestBand1Member("Jan", "Nowak " + marker, null);
        jdbcTemplate.update(
                "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                "VALUES (?, ?, '1988-01-02', false, CURRENT_DATE, false, 1)",
                "Zosia " + marker, "Wykluta " + marker);
    }
}
