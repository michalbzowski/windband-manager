package pl.michalbzowski.windband.adapter.in.web;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wpisywanie do paska wyszukiwania traciło fokus przy każdej podmiance HTMX: po serii
 * klawiszy pole było ostre tylko do chwili powrotu fragmentu; potem kursor lądował na
 * &lt;body&gt; — user musiał klikać pole ponownie, żeby pisać dalej.
 *
 * <p>Test celowo nie klika pola między literami: każda kolejna idzie tam, gdzie akurat jest
 * fokus (Actions bez elementu). Gdyby fokus zniknął, litery spadłyby na tło i wartość pola
 * pozostałaby niepełna — dokładnie ta regresa, którą tu blokujemy. Podmiany liczy licznik
 * rejestrowany w dokumencie na htmx:afterSwap, więc test czeka na realną podmiankę,
 * nie na dowolne "uspokojenie" strony.
 */
class MemberListSearchFocusUiTest extends UiTestBase {

    private String marker;

    @Test
    void searchFieldKeepsFocusWhileLiveFilterSwaps() {
        seedBand();
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(d -> !d.findElements(By.cssSelector("#member-filters input[name='q']")).isEmpty());

        // Zmiana z zadania: segment Aktywni/Nieaktywni zastępuje pojedynczy przycisk toggle...
        List<WebElement> chips = driver.findElements(By.cssSelector(".member-status-chip"));
        assertThat(chips).as("segment widoku to dokładnie dwa warianty").hasSize(2);
        for (WebElement chip : chips) {
            assertThat(chip.getAttribute("aria-pressed")).isIn("true", "false");
        }
        // ...a martwy drugi stan ("rezygnujący" to tylko data członkostwa, nie osobny model) znika z widoku.
        assertThat(driver.getPageSource()).doesNotContain("Pokaż rezygnujących");

        executeJs("window.__swaps = 0; window.addEventListener('htmx:afterSwap', function () { window.__swaps++; });");

        // Jedyny raz klika pole — jak człowiek przed pierwszą literą.
        driver.findElement(By.cssSelector("#member-filters input[name='q']")).click();
        assertThat(focusedName()).as("klik daje fokus w polu wyszukiwania").isEqualTo("q");

        String term = "Kowalska";
        for (int i = 0; i < term.length(); i++) {
            long swapsBefore = swapCount();
            new Actions(driver).sendKeys(String.valueOf(term.charAt(i))).perform();
            waitSwapsToExceed(swapsBefore);

            assertThat(focusedName())
                    .as("litera %d wywołała live-filter, a potem fokus wrócił do pola", i + 1)
                    .isEqualTo("q");
            if (swapCount() == swapsBefore) {
                // FluentWait powyżej powinien ten stan zablokować — garda diagnostyczna
                throw new AssertionError("litera " + (i + 1) + " nie doprowadziła do podmiany HTMX");
            }
        }

        String value = driver.findElement(By.cssSelector("#member-filters input[name='q']")).getAttribute("value");
        assertThat(value).as("ciągłość pisania dała pełne hasło").isEqualTo(term);
        assertThat(seededNames()).containsExactly("Anna Kowalska " + marker);
    }

    // ---- harness ---------------------------------------------------------------

    private long swapCount() {
        Object value = executeJs("return window.__swaps || 0");
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("licznik swapów nie jest liczbą: " + value);
    }

    private void waitSwapsToExceed(long before) {
        new FluentWait<>(driver)
                .ignoring(StaleElementReferenceException.class)
                .withMessage("HTMX nie podmienił fragmentu po kolejnej literze")
                .pollingEvery(Duration.ofMillis(200))
                .withTimeout(Duration.ofSeconds(20))
                .until(d -> swapCount() > before);
    }

    private String focusedName() {
        Object name = executeJs("return (document.activeElement && document.activeElement.name) || null");
        return name == null ? "" : name.toString();
    }

    private Object executeJs(String script) {
        return ((JavascriptExecutor) driver).executeScript(script);
    }

    private List<String> visibleNames() {
        List<String> names = new java.util.ArrayList<>();
        for (WebElement row : driver.findElements(By.cssSelector("#members-table tbody tr"))) {
            try {
                if (!row.isDisplayed()) {
                    continue;
                }
                String text = row.findElement(By.tagName("td")).getText().trim();
                if (!text.isEmpty()) {
                    names.add(text);
                }
            } catch (StaleElementReferenceException swappedAway) {
                // HTMX podmienia tabelę w trakcie odczytu — wracamy pustą listą, czekanie ponowi
                return List.of();
            }
        }
        return names;
    }

    /** Tylko wiersze z tej rundy — bazowy seed ma innych członków, którzy psują liczenie. */
    private List<String> seededNames() {
        return visibleNames().stream().filter(n -> n.contains(marker)).toList();
    }

    private void seedBand() {
        marker = UUID.randomUUID().toString().substring(0, 8);
        Long trabka = createTestBand1Instrument("Trabka-" + marker, 40);
        // Anna ma nazwisko w całym haślu „Kowalska”; Jan i Piotr to tło do odfiltrowania
        Long anna = createTestBand1Member("Anna", "Kowalska " + marker, null);
        jdbcTemplate.update(
                "INSERT INTO member_instruments (member_id, instrument_id, is_primary) VALUES (?, ?, ?)",
                anna, trabka, true);
        createTestBand1Member("Jan", "Nowak " + marker, null);
        createTestBand1Member("Piotr", "Nowak " + marker, null);
    }
}
