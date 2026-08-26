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
 * Verifies that after creating a member the new row is highlighted (green via the
 * shared {@code .highlight-row} class) and scrolled into view on the members list.
 *
 * <p>This proves the unified focus/highlight mechanism (initFocusHighlight in
 * windband-utils.js, driven by {@code data-focus-id} on the list container) works
 * for the members list — a regression the user reported (new members were no
 * longer highlighted after a prior change).
 */
class MemberListFocusHighlightUiTest extends UiTestBase {

    @Test
    void newMember_isHighlightedAndScrolledIntoView() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Focus" + uid;
        String lastName = "Member" + uid;

        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();

        // The list reloads via HTMX with ?focus=<newId>; the shared initFocusHighlight
        // adds .highlight-row to the new row.
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#members-content table tbody tr.highlight-row")));
        WebElement highlighted = driver.findElement(
                By.cssSelector("#members-content table tbody tr.highlight-row"));
        System.out.println("[TEST] highlighted row text: " + highlighted.getText());
        assertThat(highlighted.getText()).contains(firstName).contains(lastName);

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
