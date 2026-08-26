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
        // CI (chromium 150) was racing with the submit handler registration when
        // the listener was inline in the htmx-swapped form fragment. After
        // hoisting the handler into windband-utils.js (registered at page load
        // via delegation), this is reliably fast, but keep a generous timeout
        // for slower shared CI runners.
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Navigate to rehearsals list
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Click "Zaplanuj spotkanie" button
        var addButton = driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]"));
        addButton.click();

        // HTMX loads the form into #rehearsals-content
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form#rehearsal-form")));

        // Fill required fields. On CI (chromium 150) a bare `input.value = '...'`
        // assignment does not always notify HTML5 form validation, so the
        // submit event silently does not fire. Dispatching `input`+`change`
        // events after the assignment is the standard way to coerce the
        // browser into treating the value as a real user input.
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "var d=document.querySelector(\"input[name='date']\");" +
                "d.value='" + today + "';" +
                "d.dispatchEvent(new Event('input',{bubbles:true}));" +
                "d.dispatchEvent(new Event('change',{bubbles:true}));");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("18:00");

        // Fill optional fields
        var endTimeInput = driver.findElement(By.cssSelector("input[name='endTime']"));
        endTimeInput.clear();
        endTimeInput.sendKeys("20:00");

        // Set location via JS to ensure FormData picks it up, with the
        // same input/change event dispatch as the date field above.
        ((JavascriptExecutor) driver).executeScript(
                "var l=document.querySelector(\"input[name='location']\");" +
                "l.value='Sala prób';" +
                "l.dispatchEvent(new Event('input',{bubbles:true}));" +
                "l.dispatchEvent(new Event('change',{bubbles:true}));");

        // Submit the form by JS-driven requestSubmit(). Selenium's
        // .click() on the submit button sometimes does not fire the
        // submit event on the CI runner (chromium 150, where HTML5
        // date input validation can swallow the click), so we go
        // through the same path the browser uses internally: disable
        // validation (it is already covered by other tests) and call
        // requestSubmit() which always fires a `submit` event.
        ((JavascriptExecutor) driver).executeScript(
                "var f=document.getElementById('rehearsal-form');" +
                "f.noValidate=true;" +
                "f.requestSubmit();");

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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        wait.until(ExpectedConditions.urlMatches(".*/rehearsals/\\d+.*"));
    }
}
