package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #321 — regresja wpisywania podczas żądania. Pasek filtrów listy członków
 * nie może być podmieniany przez HTMX: wcześniejszy układ wymieniał cały
 * {@code #members-content} razem z inputem wyszukiwarki, więc litera wpisana
 * w trakcie trwającego odpytania znikała — odpowiedź serwera niosła stary parametr
 * {@code q} i nadpisywała świeżo wpisany znak.
 *
 * <p>Test dowodzi dwóch rzeczy:
 * <ol>
 *   <li>żądania filtrów celują WYŁĄCZNIE w rejon listy ({@code #member-list-region}),
 *       a nie w całość widoku,</li>
 *   <li>wpisanie ciągu w dwóch turach — z których druga pada w środek requestu —
 *       zachowuje pełną wartość, kursor i tożsamość węzła inputu (węzeł żyje,
 *       nie jest podmieniany).</li>
 * </ol>
 */
class MemberListFilterTypingUiTest extends UiTestBase {

    private static final By SEARCH = By.cssSelector("#member-filters input[name='q']");

    private boolean latencyEnabled = false;

    @AfterEach
    void releaseNetworkConditions() {
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
            // reset opóźnienia to wygodnictwo — każda kolejna klasa zaczyna od nawigacji
        }
        latencyEnabled = false;
    }

    @Test
    void literaWpisanaWTokcieZadaniaNieZnika_pasekFiltrowNieJestPodmieniany() throws Exception {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        createTestBand1Member("Anna", "Kowalska-" + marker, null); // pasuje do "Kowalsk"
        createTestBand1Member("Jan", "Kowalczyk-" + marker, null); // pasuje do "K", nie do "Kowalsk"
        createTestBand1Member("Piotr", "Nowak-" + marker, null);   // nie pasuje wcale

        loginAndNavigateTo("/members");
        ((org.openqa.selenium.chromium.HasCdp) driver).executeCdpCommand(
                "Network.emulateNetworkConditions", Map.of(
                        "offline", false,
                        "latency", 900,
                        "downloadThroughput", -1,
                        "uploadThroughput", -1));
        latencyEnabled = true;
        awaitLoaded().until(ExpectedConditions.presenceOfElementLocated(SEARCH));

        // 1. Kontrakt: filtry odpytują tylko rejon listy — pasek zostaje na miejscu.
        WebElement search = driver.findElement(SEARCH);
        assertThat(search.getAttribute("hx-target"))
                .as("wyszukiwarka podmienia wyłącznie rejon listy")
                .isEqualTo("#member-list-region");
        assertThat(search.getAttribute("hx-get"))
                .as("ścieżka żądań filtrów to endpoint samych wierszy")
                .isEqualTo("/members/list/rows");

        // Znak tożsamości na żywym węźle inputu — podmiana zmyłaby atrybut,
        // bo serwer renderuje input bez niego.
        js("arguments[0].setAttribute('data-node-mark', 'zywy'); return true;", search);

        // 2. Pierwsza tura: "K" — request startuje i trwa (opóźnione ładowanie).
        clickIntoSearch();
        typeIntoSearch("K");
        waitForLoaderVisible("po literze K");

        // 3. Druga tura WPADAJĄCA W ŚRODEK żądania: dokładamy "owalsk" BEZ ponownego
        //    klikania — input wciąż ma fokus z tury pierwszej, wysyłamy czyste klawisze
        //    do aktywnego elementu (dokładnie to, co robi użytkownik na żywym urządzeniu).
        new Actions(driver).sendKeys("owalsk").perform();

        // Wpis musi ostać się w całości — także w oknie, gdy nadchodzi odpowiedź
        // dla "K", która kiedyś nadpisywała wartość pola.
        assertThat(String.valueOf(js(
                "return document.querySelector('#member-filters input[name=q]').value;")))
                .as("wartość inputu prosto po wpisaniu w trakcie żądania")
                .isEqualTo("Kowalsk");

        waitForLoaderHidden("po odpowiedzi na pełny wpis");
        // Rejon listy odświeżony przez sfinalizowane żądanie: nasza Kowalska jest,
        // a oba wcześniejsze filtry (Kowalczyk/Nowak) wypadły.
        awaitLoaded().until(d -> Boolean.TRUE.equals(js(
                "var rows=document.querySelectorAll('#member-list-region tbody tr');" +
                "var txt=Array.prototype.map.call(rows,function(r){return r.textContent;}).join('|');" +
                "return txt.indexOf('Kowalska-') >= 0 && txt.indexOf('Kowalczyk-') < 0 && txt.indexOf('Nowak-') < 0;")));

        // 4. Ten SAM węzeł inputu, ten sam kursor na końcu — pasek przetrwał wymiany.
        assertThat(String.valueOf(js(
                "return document.querySelector('#member-filters input[name=q]').value;")))
                .as("wpis po podmianie rejonu")
                .isEqualTo("Kowalsk");
        assertThat(String.valueOf(js(
                "var el=document.querySelector('#member-filters input[name=q]');" +
                "return el ? el.getAttribute('data-node-mark') : 'podmieniony';")))
                .as("węzeł inputu nie został podmieniany (znikające litery)")
                .isEqualTo("zywy");
        assertThat(String.valueOf(js(
                "var el=document.querySelector('#member-filters input[name=q]');" +
                "return el && el.selectionStart != null ? String(el.selectionStart) : '-1';")))
                .as("kursor na końcu wpisu")
                .isEqualTo("7");
    }

    // ---- pomocnicze ------------------------------------------------------------

    /** Baza nie wystawia pola `wait` — własny WebDriverWait z hojnym timeoutem. */
    private org.openqa.selenium.support.ui.WebDriverWait awaitLoaded() {
        return new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(30));
    }

    private Object js(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    /** Klik z odpornością na stale — trafienie w podmianę nie wywala testu. */
    private void clickIntoSearch() {
        RuntimeException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                driver.findElement(SEARCH).click();
                return;
            } catch (StaleElementReferenceException |
                     org.openqa.selenium.ElementNotInteractableException stale) {
                last = stale;
                sleep(120);
            }
        }
        throw last;
    }

    /** SendKeys z ponowieniem — pisanie w środku podmiany daje stale. */
    private void typeIntoSearch(String text) {
        RuntimeException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                new Actions(driver).sendKeys(driver.findElement(SEARCH), text).perform();
                return;
            } catch (StaleElementReferenceException |
                     org.openqa.selenium.ElementNotInteractableException stale) {
                last = stale;
                sleep(120);
            }
        }
        throw last;
    }

    private void waitForLoaderVisible(String context) {
        new FluentWait<>(driver)
                .ignoring(StaleElementReferenceException.class)
                .withMessage("wskaźnik ładowania nie pojawił się " + context)
                .pollingEvery(Duration.ofMillis(10))
                .withTimeout(Duration.ofSeconds(20))
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "var el = document.querySelector('#member-list-loading');" +
                        "return !!el && getComputedStyle(el).visibility === 'visible';")));
    }

    private void waitForLoaderHidden(String context) {
        new FluentWait<>(driver)
                .ignoring(StaleElementReferenceException.class)
                .withMessage("wskaźnik ładowania utknął " + context)
                .pollingEvery(Duration.ofMillis(50))
                .withTimeout(Duration.ofSeconds(30))
                .until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                        "var el = document.querySelector('#member-list-loading');" +
                        "return !el || getComputedStyle(el).visibility === 'hidden';")));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
