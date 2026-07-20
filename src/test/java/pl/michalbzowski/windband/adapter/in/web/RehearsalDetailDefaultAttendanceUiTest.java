package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default attendance state for a freshly created rehearsal.
 *
 * <p>After creating a rehearsal there are NO attendance records in the DB, so every
 * member's status select in the detail view MUST default to NO_RESPONSE — never PRESENT.
 * This guards against the regression where a new rehearsal showed all members as PRESENT.
 */
class RehearsalDetailDefaultAttendanceUiTest extends UiTestBase {

    @Test
    void newRehearsal_showsAllMembersAsNoResponse_notPresent() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Open the "Zaplanuj spotkanie" form
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");
        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("18:00");
        var endTimeInput = driver.findElement(By.cssSelector("input[name='endTime']"));
        endTimeInput.clear();
        endTimeInput.sendKeys("20:00");
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='location']\").value = 'Sala prób';");

        // Submit
        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for the save to complete (toast + redirect away from /new)
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Navigate directly to the detail view of the newly created rehearsal.
        // In a fresh H2 test DB the first created rehearsal has id=1.
        driver.get(baseUrl() + "/rehearsals/1");
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("select[id^='status_']")));

        // Verify every member's status select defaults to NO_RESPONSE
        List<org.openqa.selenium.WebElement> selects = driver.findElements(
                By.cssSelector("select[id^='status_']"));
        assertThat(selects)
                .as("There should be at least one member status select")
                .isNotEmpty();

        for (var select : selects) {
            String value = select.getAttribute("value");
            System.out.println("[TEST] member select " + select.getAttribute("id") + " value=" + value);
            assertThat(value)
                    .as("Fresh rehearsal: member %s must default to NO_RESPONSE, not PRESENT",
                            select.getAttribute("id"))
                    .isEqualTo("NO_RESPONSE");
        }
    }
}
