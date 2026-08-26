package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for Issue #118: Hide invitation sending section for past events.
 *
 * The "📧 Wyślij zaproszenia" (Send invitations) section should be hidden when an event's date is in the past
 * (today or earlier). This test suite covers all specified acceptance criteria:
 * - Check event end date against current date at page load
 * - If event has already occurred, hide the entire "📧 Wyślij zaproszenia" section including button
 * - Section remains visible for future events only
 * - Test with events dated: yesterday, today, tomorrow, next week
 */
class EventPastDateInvitationVisibilityUiTest extends UiTestBase {

    private WebDriverWait waitHelper() {
        return new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    /**
     * Helper method to create a test event with the specified date.
     * Used by UI tests that need events on specific dates (past/today/future).
     */
    protected Long createEventWithDate(String name, LocalDate date) {
        String sql = """
                INSERT INTO band_events (name, date, start_time, location, event_type, payment_type, payment_amount, notes, band_id)
                VALUES (?, ?, '18:00', 'Test Location', 'CONCERT', 'FREE', 0, 'Test event for issue #118', 1)
            """;
        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, date.toString());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    @Test
    void pastEventYesterday_invitationSectionHidden() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Long eventId = createEventWithDate("Koncert wczoraj", yesterday);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The invitation sending section should NOT be visible for past events
        By invitationSectionLocator = By.xpath("//h3[contains(., '📧 Wyślij zaproszenia')]");

        try {
            waitHelper().until(ExpectedConditions.presenceOfElementLocated(invitationSectionLocator));
            assertThat(false).as("Invitation section should be hidden for event dated " + yesterday).isTrue();
        } catch (org.openqa.selenium.TimeoutException e) {
            // Expected: element not present = section is properly hidden
        }

        final WebElement[] foundElement = {null};
        try {
            foundElement[0] = driver.findElement(invitationSectionLocator);
            assertThat(false).as("Invitation sending section should be absent for past event (yesterday)").isTrue();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            // This is the expected behavior - element doesn't exist in DOM
        }
    }

    @Test
    void pastEventToday_invitationSectionHidden() {
        LocalDate today = LocalDate.now();
        Long eventId = createEventWithDate("Koncert dzisiaj", today);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The invitation section should be hidden for events dated today
        By invitationSectionLocator = By.xpath("//h3[contains(., '📧 Wyślij zaproszenia')]");

        try {
            waitHelper().until(ExpectedConditions.presenceOfElementLocated(invitationSectionLocator));
            assertThat(false).as("Invitation section should be hidden for event dated " + today).isTrue();
        } catch (org.openqa.selenium.TimeoutException e) {
            // Expected: element not present = section is properly hidden
        }

        final WebElement[] foundElement = {null};
        try {
            foundElement[0] = driver.findElement(invitationSectionLocator);
            assertThat(false).as("Invitation sending section should be absent for event dated today").isTrue();
        } catch (org.openqa.selenium.NoSuchElementException e) {
            // Expected behavior - element doesn't exist in DOM
        }
    }

    @Test
    void futureEventTomorrow_invitationSectionVisible() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Long eventId = createEventWithDate("Koncert jutro", tomorrow);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The invitation section should be visible for future events
        By invitationSectionLocator = By.xpath("//h3[contains(., '📧 Wyślij zaproszenia')]");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(invitationSectionLocator));
        assertThat(element.isDisplayed()).as("Invitation sending section should be visible for future event (tomorrow)").isTrue();

        // Also verify the "Send to all" button is present (part of the same section)
        WebElement sendAllButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("send-all-btn")));
        assertThat(sendAllButton.isDisplayed()).as("Send invitations button should be visible").isTrue();
    }

    @Test
    void futureEventNextWeek_invitationSectionVisible() {
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        Long eventId = createEventWithDate("Koncert za tydzień", nextWeek);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The invitation section should be visible for future events
        By invitationSectionLocator = By.xpath("//h3[contains(., '📧 Wyślij zaproszenia')]");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(invitationSectionLocator));
        assertThat(element.isDisplayed()).as("Invitation sending section should be visible for future event (next week)").isTrue();
    }

    @Test
    void pastEventOtherSectionsRemainVisible() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Long eventId = createEventWithDate("Koncert wczoraj test", yesterday);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Verify that other sections are still visible (not affected by hiding invocation section)

        // "Zaproś na wydarzenie" section should still be visible (different from invitation sending)
        By inviteSectionLocator = By.xpath("//h3[contains(., 'Zaproś na wydarzenie')]");
        WebElement element = waitHelper().until(ExpectedConditions.presenceOfElementLocated(inviteSectionLocator));
        assertThat(element.isDisplayed()).as("Invite participants section remains visible").isTrue();

        // Event name should be displayed
        String title = driver.findElement(By.cssSelector("#events-content article header strong")).getText();
        assertThat(title).isEqualTo("Koncert wczoraj test");
    }
}
