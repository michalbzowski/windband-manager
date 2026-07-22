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
 * UI test reproducing issue #90: clicking "Zapisz i dodaj kolejny" on the
 * rehearsal form redirects to the list instead of staying on a cleared form.
 *
 * <p>Scenario: open the rehearsal form, fill required fields, click
 * "Zapisz i dodaj kolejny", and verify the form stays on the same page
 * with cleared fields (not redirected to the list).</p>
 */
class RehearsalSaveAndAddAnotherUiTest extends UiTestBase {

    @Test
    void shouldStayOnForm_afterClickingSaveAndAddAnother() throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj spotkanie" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        // Wait for form to load
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("18:00");

        // Fill location
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='location']\").value = 'Sala prób';");

        // === Click "Zapisz i dodaj kolejny" button ===
        var saveAndAddBtn = driver.findElement(
                By.cssSelector("button[name='saveAndAddAnother']"));
        saveAndAddBtn.click();

        // Wait for the location field to be cleared by the post-save form reset
        // (replaces fixed Thread.sleep — waits on stable DOM state, not time)
        wait.until(d -> {
            String v = (String) ((JavascriptExecutor) d).executeScript(
                    "var i = document.querySelector(\"input[name='location']\");" +
                    "return i ? i.value : null;");
            return v != null && v.isEmpty();
        });

        // === Verify we are still on the form page (NOT redirected to list) ===
        // The form should still be visible
        String currentUrl = driver.getCurrentUrl();
        assertThat(currentUrl)
                .as("After 'Zapisz i dodaj kolejny', should stay on the form page, not redirect to list")
                .doesNotContain("/rehearsals/list");

        // The form should still be present
        boolean formStillPresent = (boolean) ((JavascriptExecutor) driver)
                .executeScript("return document.getElementById('rehearsal-form') !== null;");
        assertThat(formStillPresent)
                .as("Rehearsal form should still be present after 'Zapisz i dodaj kolejny'")
                .isTrue();

        // The form fields should be cleared (location should be empty)
        String locationValue = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector(\"input[name='location']\") ? document.querySelector(\"input[name='location']\").value : '';");
        assertThat(locationValue)
                .as("Location field should be cleared after 'Zapisz i dodaj kolejny'")
                .isEmpty();

        // Verify the date field is reset to today
        String dateValue = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector(\"input[name='date']\") ? document.querySelector(\"input[name='date']\").value : '';");
        assertThat(dateValue)
                .as("Date field should be reset to today after 'Zapisz i dodaj kolejny'")
                .isEqualTo(today);
    }

    @Test
    void shouldRedirectToList_afterClickingRegularSave() throws InterruptedException {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj spotkanie" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        // Wait for form to load
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("19:00");

        // === Click regular "Zaplanuj" button (NOT "Zapisz i dodaj kolejny") ===
        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for redirect to list
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // === Verify we are redirected to the list ===
        String currentUrl = driver.getCurrentUrl();
        assertThat(currentUrl)
                .as("After regular save, should redirect to rehearsals list")
                .contains("/rehearsals");
    }
}
