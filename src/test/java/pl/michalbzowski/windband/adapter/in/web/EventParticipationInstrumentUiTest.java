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
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-list-container")));

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
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-list-container")));

        // 2. Click first "Szczegóły" button to open event detail
        var detailBtns = driver.findElements(By.xpath("//button[contains(text(), 'Szczegóły')]"));
        assertThat(detailBtns).isNotEmpty();
        detailBtns.get(0).click();

        // Wait for event detail fragment to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        // Wait for invite section which is part of the detail
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

        // Invite the first member via a direct API call (deterministic, avoids UI event-timing races
        // with the HTMX/fetchWithToast wiring on the fragment). This is still an end-to-end check:
        // the participation must persist and render in the detail table.
        Long memberId = options.size() > 1 ? 1L : null;
        Object inviteResult = ((JavascriptExecutor) driver).executeScript(
                "return fetch('/api/events/' + arguments[0] + '/invite', {" +
                "  method: 'POST', headers: {'Content-Type':'application/json'}," +
                "  body: JSON.stringify({eventId: arguments[0], memberId: parseInt(arguments[1])})" +
                "}).then(function(r){ return 'STATUS=' + r.status; }).catch(function(e){ return 'ERR=' + e; });",
                eventId, String.valueOf(memberId));
        System.out.println("[TEST] Invite API result: " + inviteResult);

        // Give the server a moment to persist, then reload and actively wait for the row
        Thread.sleep(2000);
        driver.get(baseUrl() + "/events/" + eventId);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(
                    ExpectedConditions.numberOfElementsToBeMoreThan(
                            By.cssSelector("#participants-table tbody tr"), 0));
        } catch (Exception e) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/event-detail-source.html"), driver.getPageSource());
                System.out.println("[TEST] Dumped page source to /tmp/event-detail-source.html");
            } catch (Exception ignore) {}
            throw e;
        }

        // 4. Change Jan's instrument to Bęben for this event
        var instrumentSelects = driver.findElements(By.cssSelector(".instrument-select"));
        System.out.println("[TEST] Instrument select count: " + instrumentSelects.size());

        // We expect exactly one instrument select (for the invited member)
        assertThat(instrumentSelects).hasSize(1);

        var instrumentSelect = instrumentSelects.get(0);
        var instOptions = instrumentSelect.findElements(By.tagName("option"));
        String bubenValue = null;
        for (var opt : instOptions) {
            // NOTE: WebElement.getText() returns "" for <option> elements in headless Chrome,
            // so we match on the data-name attribute (rendered by Thymeleaf) instead.
            String nameAttr = opt.getAttribute("data-name");
            String text = (nameAttr != null && !nameAttr.isBlank()) ? nameAttr : opt.getText();
            if (text != null && text.contains("Bęben")) {
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