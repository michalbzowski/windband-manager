package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that after creating an event the new row is highlighted (green via the
 * shared {@code .highlight-row} class) and scrolled into view on the events list.
 *
 * <p>Regression for the user-reported bug: after adding an event the page scrolled
 * to the top and never highlighted/scrolls to the new event (the highlight code
 * ran on the old page context that was destroyed by the full navigation). The
 * unified initFocusHighlight now runs on the reloaded list via {@code ?focus=}.
 */
class EventListFocusHighlightUiTest extends UiTestBase {

    @Test
    void newEvent_isHighlightedAndScrolledIntoView() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String name = "FocusEvent" + uid;

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        fill("name", name);
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();

        // Full navigation to /events?focus=<newId> -> list reloads, initFocusHighlight runs.
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#events-list-container table tbody tr.highlight-row")));
        WebElement highlighted = driver.findElement(
                By.cssSelector("#events-list-container table tbody tr.highlight-row"));
        System.out.println("[TEST] highlighted event row text: " + highlighted.getText());
        assertThat(highlighted.getText()).contains(name);

        // Wait for scroll animation: poll the bounding-rect until the row is inside the viewport
        wait.until(d -> Boolean.TRUE.equals(((JavascriptExecutor) d).executeScript(
                "var r = arguments[0].getBoundingClientRect();" +
                "return r.top < window.innerHeight && r.bottom > 0;", highlighted)));
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
