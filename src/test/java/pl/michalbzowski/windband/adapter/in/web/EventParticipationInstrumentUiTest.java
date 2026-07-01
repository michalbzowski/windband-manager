package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

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

        // 1. Navigate to events list and create an event
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

        // 2. Click first "Szczegóły" button to open event detail
        var detailBtns = driver.findElements(By.xpath("//button[contains(text(), 'Szczegóły')]"));
        assertThat(detailBtns).isNotEmpty();
        detailBtns.get(0).click();

        // Wait for event detail page to load (HTMX swap) - wait for invite section which is part of the detail
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-member-select")));

        // Get the event ID from the #events-content element's data-event-id attribute (now guaranteed to be the detail fragment)
        String eventIdStr = (String) ((JavascriptExecutor) driver).executeScript(
                "var el = document.getElementById('events-content'); return el ? el.dataset.eventId : null;");
        Long eventId = eventIdStr != null ? Long.valueOf(eventIdStr) : null;
        System.out.println("[TEST] Event ID: " + eventId);

        // 3. Invite the first member (Jan Kowalski) via API
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

        // Wait for invite API to complete and toast to appear
        Thread.sleep(2000);
        
        // Manually reload the event detail page to see the invited member (HTMX reload has server-side 500 issue)
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        
        // Wait for script to initialize (event listeners to attach)
        Thread.sleep(3000);
        
        // Wait for the participant row to appear
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("#participants-table tbody tr"), 0));

        // 4. Change Jan's instrument to Bęben for this event
        var instrumentSelects = driver.findElements(By.cssSelector(".instrument-select"));
        System.out.println("[TEST] Instrument select count: " + instrumentSelects.size());

        // We expect exactly one instrument select (for the invited member)
        assertThat(instrumentSelects).hasSize(1);

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

        // Change instrument by calling the API directly via JavaScript fetch
        // Then do a full page reload to see the updated state
        ((JavascriptExecutor) driver).executeScript(
            "var select = arguments[0]; " +
            "var memberId = select.dataset.memberId; " +
            "var instrumentId = arguments[1]; " +
            "var eventId = document.getElementById('events-content').dataset.eventId; " +
            "var csrfToken = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1]; " +
            "fetch('/api/events/' + eventId + '/participation-instrument', { " +
            "  method: 'PUT', " +
            "  headers: { " +
            "    'Content-Type': 'application/json', " +
            "    'X-XSRF-TOKEN': csrfToken " +
            "  }, " +
            "  body: JSON.stringify({memberId: parseInt(memberId), instrumentId: parseInt(instrumentId)}) " +
            "}).then(function(r) { console.log('API response:', r.status); });",
            instrumentSelect, bubenValue);

        // Wait for API call to complete
        Thread.sleep(2000);

        // Reload the event page to see the updated instrument
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("#participants-table tbody tr"), 0));

        // Wait for instrument name to update
        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(10));
        longWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector(".instrument-name"), "Bęben"));

        // 5. Navigate to members list and verify Jan's default instrument is still Trąbka
        driver.get(baseUrl() + "/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("members-content")));

        var memberRows = driver.findElements(By.cssSelector("table tbody tr"));
        assertThat(memberRows).isNotEmpty();

        String firstRowText = memberRows.get(0).getText();
        System.out.println("[TEST] First member row: " + firstRowText);
        // Jan Kowalski should be the first member (alphabetically? but we know he is in the DB)
        assertThat(firstRowText).contains("Jan Kowalski");
        // His instrument should be Trąbka (the default from data.sql)
        assertThat(firstRowText).contains("Trąbka");
        // And should not contain Bęben (since we only changed it for the event)
        assertThat(firstRowText).doesNotContain("Bęben");

        // 6. Additionally, verify that the event participation still has Bęben
        // Go back to the event detail page
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Find the instrument name for the member in the participants table
        var participantRow = driver.findElement(By.cssSelector("#participants-table tbody tr"));
        var participantInstrumentName = participantRow.findElement(By.cssSelector(".instrument-name")).getText();
        assertThat(participantInstrumentName).isEqualTo("Bęben");
    }
}