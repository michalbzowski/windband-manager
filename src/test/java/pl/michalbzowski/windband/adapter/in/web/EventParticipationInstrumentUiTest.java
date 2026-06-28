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
 * Regression test for: changing instrument (tag) on an event participation
 * must NOT change the member's default instrument globally.
 *
 * Bug: the event detail page called PUT /api/members/{id}/instrument which
 * changed the member's permanent instrument. Fix: per-event instrument_id
 * on EventParticipation with dedicated endpoint PUT /api/events/{id}/participation-instrument.
 */
class EventParticipationInstrumentUiTest extends UiTestBase {

    @Test
    void shouldChangeInstrumentPerEventWithoutAffectingMemberDefault() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // 1. Create an event via API
        loginAndNavigateTo("/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj wydarzenie')]"));
        addButton.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var nameInput = driver.findElement(By.cssSelector("input[name='name']"));
        nameInput.clear();
        nameInput.sendKeys("Koncert Testowy");

        var submitBtn = driver.findElement(
                By.cssSelector("#event-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for save + redirect
        Thread.sleep(3000);
        driver.get(baseUrl() + "/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Click first "Szczegóły" button
        var detailBtns = driver.findElements(By.xpath("//button[contains(text(), 'Szczegóły')]"));
        assertThat(detailBtns).isNotEmpty();
        detailBtns.get(0).click();

        // Wait for event detail page to load (HTMX swap)
        Thread.sleep(2000);

        // Get the event ID from the page's JS variable
        Long eventId = (Long) ((JavascriptExecutor) driver).executeScript("return typeof eventId !== 'undefined' ? eventId : null;");
        System.out.println("[TEST] Event ID: " + eventId);

        // Wait for invite section to be present
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-member-select")));

        // 2. Invite the first member (Jan Kowalski) via API
        var memberSelect = driver.findElement(By.id("invite-member-select"));
        var options = memberSelect.findElements(By.tagName("option"));
        System.out.println("[TEST] Member option count: " + options.size());
        for (var opt : options) {
            System.out.println("[TEST]   option: value=" + opt.getAttribute("value") + " text=" + opt.getText());
        }

        // Select first member (skip placeholder)
        if (options.size() > 1) {
            ((JavascriptExecutor) driver).executeScript(
                    "document.getElementById('invite-member-select').selectedIndex = 1;");
        }
        driver.findElement(By.id("invite-btn")).click();

        // Wait for invite to complete
        Thread.sleep(3000);

        // Hard reload the event detail page (not HTMX)
        driver.get(baseUrl() + "/events/" + eventId);
        Thread.sleep(2000);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // 3. Find the instrument select for the invited member
        var instrumentSelects = driver.findElements(By.cssSelector(".instrument-select"));
        System.out.println("[TEST] Instrument select count: " + instrumentSelects.size());

        // Debug: print page source around participants table
        var participantsTable = driver.findElements(By.cssSelector("#participants-table"));
        System.out.println("[TEST] Participants table present: " + !participantsTable.isEmpty());

        if (instrumentSelects.isEmpty()) {
            // Page source debug
            System.out.println("[DEBUG] Page source (first 3000 chars):");
            System.out.println(driver.getPageSource().substring(0, Math.min(3000, driver.getPageSource().length())));
        }

        assertThat(instrumentSelects).as("There should be at least one participant with instrument select").isNotEmpty();

        // 4. Change Jan's instrument to Bęben for this event
        var instrumentSelect = instrumentSelects.get(0);
        var instOptions = instrumentSelect.findElements(By.tagName("option"));
        String bubenValue = null;
        for (var opt : instOptions) {
            if (opt.getText().contains("Bęben")) {
                bubenValue = opt.getAttribute("value");
                break;
            }
        }
        assertThat(bubenValue).as("Bęben instrument must exist in select").isNotNull();

        // Change via JS to trigger the change event handler
        ((JavascriptExecutor) driver).executeScript(
                "var sel = arguments[0]; sel.value = arguments[1]; " +
                "sel.dispatchEvent(new Event('change'));",
                instrumentSelect, bubenValue);

        // Wait for PUT request and HTMX reload
        Thread.sleep(3000);

        // 5. Navigate to members list and verify Jan's default instrument is still Trąbka
        driver.get(baseUrl() + "/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("members-content")));

        var memberRows = driver.findElements(By.cssSelector("table tbody tr"));
        assertThat(memberRows).isNotEmpty();

        String firstRowText = memberRows.get(0).getText();
        System.out.println("[TEST] First member row: " + firstRowText);
        assertThat(firstRowText)
                .as("Member's default instrument should remain Trąbka, not Bęben (per-event override must not affect member profile)")
                .contains("Trąbka")
                .doesNotContain("Bęben");
    }
}
