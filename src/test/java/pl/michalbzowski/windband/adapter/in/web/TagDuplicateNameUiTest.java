package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #269 — the user-visible half of the duplicate-tag fix.
 *
 * <p>Adding a tag whose name already exists in the team used to end in a silent
 * failure: the API answered 500 (raw {@code duplicate key value violates unique
 * constraint "instruments_band_id_name_key"}), the toast printed
 * "Zapisano tag (500)", and the form was thrown away — no clue what was wrong.
 *
 * <p>Now the command layer rejects the duplicate with a Polish sentence and the
 * form keeps the typed value so it can be corrected.
 */
class TagDuplicateNameUiTest extends UiTestBase {

    @Test
    void addingASecondTagWithTheSameNameExplainsItself() {
        String name = "Powtarzalny Tag " + java.util.UUID.randomUUID().toString().substring(0, 8);
        createTestBand1Instrument(name);

        // /tags/new is a bare HTMX fragment (no #content wrapper), so the form is opened
        // the way a user opens it: from the list, through the "Dodaj tag" action.
        loginAndNavigateTo("/tags");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        wait.until(d -> d.findElement(By.id("add-tag-btn"))).click();
        wait.until(d -> d.findElement(By.cssSelector("#tag-form input[name='name']")));

        WebElement field = driver.findElement(By.cssSelector("#tag-form input[name='name']"));
        field.clear();
        field.sendKeys(name);
        js.executeScript("document.querySelector('#tag-form button[type=submit]').click();");

        // The reason must reach the user, not a bare status code.
        WebElement toast = wait.until(d -> d.findElements(By.cssSelector("#toast-container .toast.error"))
                .stream()
                .filter(t -> t.isDisplayed() && !t.getText().isBlank())
                .findFirst()
                .orElse(null));
        assertThat(toast.getText())
                .as("error toast explains the conflict — was: %s", toast.getText())
                .contains("już istnieje")
                .contains(name);

        // And the form must stay put with the value still typed (no bounce to the list).
        assertThat(driver.findElements(By.cssSelector("#tag-form input[name='name']")))
                .as("the form stays open so the name can be corrected")
                .isNotEmpty();
        assertThat(wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#tag-form input[name='name']"))).getAttribute("value"))
                .isEqualTo(name);
    }
}
