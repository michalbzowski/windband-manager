package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Reproduces and verifies the fix for issue #82:
 * "Nie da się zdefiniować próby" — submitting the rehearsal form does not save.
 *
 * <p>The root cause was a JavaScript syntax error in the fetchWithToast call:
 * the opening brace for the second argument was on the same line as the first
 * argument without proper separation, causing a JS parse error. The fix corrects
 * the syntax so the fetch call executes properly.
 *
 * <p>This test verifies:
 * <ol>
 *   <li>Navigating to the rehearsals page</li>
 *   <li>Clicking "Zaplanuj spotkanie" to open the form</li>
 *   <li>Filling required fields and submitting</li>
 *   <li>Verifying the rehearsal appears in the list (toast + redirect)</li>
 * </ol>
 */
class RehearsalSaveUiTest extends UiTestBase {

    @Test
    void shouldSaveRehearsal_andRedirectToList() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj spotkanie" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        // HTMX loads the form into #rehearsals-content
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields
        // Set date using JS to ensure correct format for input[type=date]
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("18:00");

        // Fill optional fields
        var endTimeInput = driver.findElement(By.cssSelector("input[name='endTime']"));
        endTimeInput.clear();
        endTimeInput.sendKeys("20:00");

        // Set location via JS to ensure FormData picks it up
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='location']\").value = 'Sala prób';");
        // Verify the value was set
        String locationVal = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.querySelector(\"input[name='location']\").value;");
        System.out.println("[TEST] location value after JS set: '" + locationVal + "'");

        // Submit the form by clicking "Zaplanuj" button
        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for redirect to rehearsals list (happens after 1.5s delay for toast)
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        // Make sure we're NOT on /rehearsals/new or a detail page
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlMatches(".*/rehearsals/\\d+.*")));

        // Verify we landed on the rehearsals list page
        String currentUrl = driver.getCurrentUrl();
        System.out.println("[TEST] current URL after save: " + currentUrl);
        assertThat(currentUrl)
                .as("Should redirect to rehearsals list after save")
                .contains("/rehearsals");
    }

    @Test
    void shouldShowToastAfterSavingRehearsal() throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj spotkanie" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        // Wait for form to load
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsal-form")));

        // Fill required fields
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("19:00");

        // Submit
        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for toast to appear on the form page (before redirect)
        // Toast is shown by fetchWithToast after successful save
        WebDriverWait toastWait = new WebDriverWait(driver, Duration.ofSeconds(5));
        toastWait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#toast-container .toast, .toast-message, [class*='toast']")));

        // Verify toast content
        String toastText = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('toast-container') ? document.getElementById('toast-container').textContent : '';");
        System.out.println("[TEST] toast-container text: '" + toastText + "'");
        assertThat(toastText)
                .as("Toast with success message should appear after saving a rehearsal")
                .contains("Zapisano spotkanie");
    }
}
