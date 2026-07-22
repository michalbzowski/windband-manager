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
 * Verifies the post-create navigation flow for rehearsals.
 *
 * <p>Previously the rehearsal form redirected to {@code /rehearsals?focus=...}
 * (the list page with a green highlight on the new row). The user changed
 * this so the form now navigates straight to {@code /rehearsals/{id}} —
 * the detail page — so the admin can immediately click "Zaproś
 * uczestników" / "Zaproś grupę". The "Zapisz i dodaj kolejny" button still
 * stays on the form (same as the old flow).</p>
 *
 * <p>This test replaces the old RehearsalSaveUiTest which asserted on
 * the list-page redirect. We assert on the new detail-page redirect here.
 * The toast assertion test is preserved — the form still calls
 * {@code fetchWithToast} so the success toast still fires before the
 * navigation.</p>
 */
class RehearsalSaveUiTest extends UiTestBase {

    @Test
    void shouldSaveRehearsal_andRedirectToDetail() {
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

        // Submit the form by clicking "Zaplanuj" button
        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // The new flow navigates to /rehearsals/{id} (detail), not /rehearsals (list).
        waitForDetailRedirect();

        // Verify we landed on the new rehearsal's detail page (not on the list)
        String currentUrl = driver.getCurrentUrl();
        System.out.println("[TEST] current URL after save: " + currentUrl);
        assertThat(currentUrl)
                .as("Should redirect to the rehearsal detail page (/rehearsals/{id}) after save, not the list")
                .matches(".*/rehearsals/\\d+.*");
        // And explicitly NOT on /rehearsals/new (form) or /rehearsals (list root)
        assertThat(currentUrl).doesNotContain("/rehearsals/new");
    }

    /**
     * Waits until the browser is on the rehearsal detail page.
     */
    private void waitForDetailRedirect() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.urlMatches(".*/rehearsals/\\d+.*"));
    }
}
