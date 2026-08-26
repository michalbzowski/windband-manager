package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for issue #97: "Wejście w edycję członka ustawia scroll na dół widoku"
 */
class MemberEditScrollToTopUiTest extends UiTestBase {

    @Test
    void enterMemberEditScrollsToTop() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Step 1: navigate to members list
        loginAndNavigateTo("/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-table")));

        // Get the first member (any one is fine for this test)
        var rows = driver.findElements(By.cssSelector("#members-table tbody tr[data-member-id]"));
        assertThat(rows).isNotEmpty();
        WebElement targetRow = rows.get(0);

        // Step 2: click "Edytuj"
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", targetRow);
        WebElement editBtn = wait.until(ExpectedConditions.elementToBeClickable(
                targetRow.findElement(By.xpath(".//button[contains(., 'Edytuj')]"))));

        // Click via JS to avoid ElementClickInterceptedError from sticky nav/overlay
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn);

        // Wait for the form to appear (HTMX swap loaded it into #members-content)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        // Step 3: assert that page is scrolled to top
        // The fix adds window.scrollTo({ left: 0, top: 0 }) in the form's inline script.
        long scrollY = (long) ((JavascriptExecutor) driver).executeScript("return window.scrollY;");

        assertThat(scrollY)
                .as("Page should be scrolled to TOP when entering edit form")
                .isZero();
    }
}
