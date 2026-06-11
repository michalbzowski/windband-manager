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
 *   <li>Clicking "Zaplanuj próbę" to open the form</li>
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

        // Click "Zaplanuj próbę" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj próbę')]"));
        addButton.click();

        // HTMX loads the form into #rehearsals-content
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields
        var dateInput = driver.findElement(By.cssSelector("input[name='date']"));
        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));

        // Set date using JS to ensure correct format for input[type=date]
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        // Set start time
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

        // After successful save, the page should redirect to the rehearsals list
        // The list view should contain the rehearsal we just created
        try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Verify the rehearsal list is shown (not the form anymore)
        // The list should contain our rehearsal data
        String content = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.querySelector('#rehearsals-content').textContent;");
        System.out.println("[TEST] rehearsals-content text: " + content);

        // The list should show the location we entered, confirming the save
        assertThat(content)
                .as("Rehearsals list should contain the saved rehearsal's location")
                .contains("Sala prób");
    }

    @Test
    void shouldShowToastAfterSavingRehearsal() throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj próbę" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj próbę')]"));
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

        // Wait for redirect to list
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Check for toast message — the toast container should have "Zapisano próbę"
        // Toast is shown via the toast-container fragment
        Thread.sleep(500); // Brief wait for toast to appear
        String pageContent = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.body.textContent;");
        assertThat(pageContent)
                .as("Toast with success message should appear after saving a rehearsal")
                .contains("Zapisano próbę");
    }
}
