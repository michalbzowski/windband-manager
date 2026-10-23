package pl.michalbzowski.windband.webui;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import pl.michalbzowski.windband.UiTestBase;

/**
 * E2E strony „Klucze AI” (skarbiec kluczy LLM, konto użytkownika):
 * <ul>
 *   <li>zapis klucza OpenRouter przez formularz (POST /api/llm-keys + CSRF z fetchWithToast),</li>
 *   <li>tabela pokazuje wyłącznie keyHint (ostatnie 4 znaki) — jawna wartość NIGDY
 *       nie pojawia się w DOM (AES-GCM w bazie, ciphertext nie wychodzi z backendu),</li>
 *   <li>ponowny zapis NADPISUJE ten sam wiersz (jeden klucz na owner+provider).</li>
 * </ul>
 *
 * <p>Wymaga {@code app.llmkeys.master-key} w application-test.yml (skarbiec włączony).
 */
class LlmKeysPageUiTest extends UiTestBase {

    // Celowo bez wzorca "sk-o""r-v1-…": sekrety wyglądające jak prawdziwe klucze
    // OpenRouter są flagowane przez GitGuardian w CI (fałszywe pozytywne).
    private static final String PLAIN_1 = "openrouter-e2e-test-key-7890";
    private static final String PLAIN_2 = "openrouter-e2e-test-key-2468";

    @Test
    @DisplayName("Zapis klucza OpenRouter: widać hint (…7890), brak jawnej wartości w DOM, nadpisanie aktualizuje wiersz")
    void savesKeyShowsHintNeverPlaintext() {
        loginAndNavigateTo("/llm-keys");
        FluentWait<WebDriver> wait =
                new WebDriverWait(driver, Duration.ofSeconds(30)).pollingEvery(Duration.ofMillis(200));

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("llm-key-form")));
        Assertions.assertTrue(driver.getPageSource().contains("Nie zapisano jeszcze żadnego klucza")
                        || driver.getPageSource().contains("OPENROUTER"),
                "strona /llm-keys powinna pokazać stan pusty albo zapisany klucz");

        saveKey(wait, "Klucz E2E", PLAIN_1);

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("llmkeys-content"), "…7890"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("llmkeys-content"), "Klucz E2E"));
        assertNoPlaintextInDom();

        // Nadpisanie: ten sam (owner, provider) — hint się zmienia, wiersz wciąż jeden.
        saveKey(wait, "Klucz E2E v2", PLAIN_2);

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("llmkeys-content"), "…2468"));
        int rows = driver.findElements(By.cssSelector("#llmkeys-content tbody tr")).size();
        Assertions.assertEquals(1, rows, "nadpisanie klucza nie może tworzyć drugiego wiersza");
        assertNoPlaintextInDom();
    }

    private void saveKey(FluentWait<WebDriver> wait, String label, String plainKey) {
        var labelField = wait.until(ExpectedConditions.elementToBeClickable(By.id("llm-key-label")));
        labelField.clear();
        labelField.sendKeys(label);
        var keyField = wait.until(ExpectedConditions.elementToBeClickable(By.id("llm-key-value")));
        keyField.clear();
        keyField.sendKeys(plainKey);
        wait.until(ExpectedConditions.elementToBeClickable(By.id("llm-key-save-btn"))).click();
    }

    private void assertNoPlaintextInDom() {
        String dom = driver.getPageSource();
        Assertions.assertFalse(dom.contains(PLAIN_1), "jawna wartość klucza 1 wyciekła do DOM");
        Assertions.assertFalse(dom.contains(PLAIN_2), "jawna wartość klucza 2 wyciekła do DOM");
    }
}
