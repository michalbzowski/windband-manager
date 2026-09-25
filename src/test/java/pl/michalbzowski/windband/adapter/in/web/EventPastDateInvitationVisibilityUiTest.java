package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
 * The "📧 Wyślij do wszystkich" (Send invitations) action should be hidden when an event's date is in the past
 * (today or earlier). This test suite covers all specified acceptance criteria:
 * - Check event end date against current date at page load
 * - If event has already occurred, hide the "📧 Wyślij do wszystkich" action including button
 * - Action remains available for future events only
 * - Test with events dated: yesterday, today, tomorrow, next week
 *
 * <p>Issue #226 ("Przenieś akcje") relocated both actions ("Zaproś" and "Wyślij do
 * wszystkich") from floating body sections into the page header's ⋮ overflow menu.
 * The send-all action is server-rendered only for future events (hidden for past
 * ones), so for past events the button must be ABSENT from the DOM, while for
 * future events it must exist inside {@code .overflow-menu}.
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

        // The "Send invitations" action must be ABSENT from the DOM for past events.
        // It is no longer a fixed body section (moved to the ⋮ overflow menu by Issue #226),
        // so the absence-of-the-button check is stronger than the former heading check:
        // if the button were rendered at all, the hidden send-all flow would still be reachable.
        org.openqa.selenium.support.ui.WebDriverWait invisible = new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofMillis(300));
        try {
            invisible.until(ExpectedConditions.invisibilityOfElementLocated(By.id("send-all-btn")));
        } catch (org.openqa.selenium.TimeoutException ignored) {
            // Element did not appear within the poll window — that is the correct outcome.
        }
        org.assertj.core.api.Assertions.assertThat(driver.findElements(By.id("send-all-btn"))).isEmpty();
    }

    @Test
    void pastEventToday_invitationSectionHidden() {
        LocalDate today = LocalDate.now();
        Long eventId = createEventWithDate("Koncert dzisiaj", today);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The "Send invitations" action must be hidden for events dated today
        org.openqa.selenium.support.ui.WebDriverWait invisible = new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofMillis(300));
        try {
            invisible.until(ExpectedConditions.invisibilityOfElementLocated(By.id("send-all-btn")));
        } catch (org.openqa.selenium.TimeoutException ignored) {
            // Element did not appear within the poll window — that is the correct outcome.
        }
        org.assertj.core.api.Assertions.assertThat(driver.findElements(By.id("send-all-btn"))).isEmpty();
    }

    @Test
    void futureEventTomorrow_invitationSectionVisible() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Long eventId = createEventWithDate("Koncert jutro", tomorrow);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The "Send invitations" action must exist and, since Issue #226, live inside the
        // page header's ⋮ overflow menu (not as a floating body section).
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement sendAllButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("send-all-btn")));
        Object inOverflowMenu = ((JavascriptExecutor) driver).executeScript(
                "var el = arguments[0]; var bar = document.querySelector('.overflow-menu');" +
                " return !!(el && bar && bar.contains(el));",
                sendAllButton);
        assertThat(Boolean.TRUE.equals(inOverflowMenu)).as("Send invitations action should be inside the ⋮ overflow menu for a future event (tomorrow)").isTrue();
    }

    @Test
    void futureEventNextWeek_invitationSectionVisible() {
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        Long eventId = createEventWithDate("Koncert za tydzień", nextWeek);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);

        // The "Send invitations" action must be present for future events and, since Issue #226,
        // reside in the ⋮ overflow menu (the former body heading was removed).
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("send-all-btn")));
        Object inOverflowMenu = ((JavascriptExecutor) driver).executeScript(
                "var el = arguments[0]; var bar = document.querySelector('.overflow-menu');" +
                " return !!(el && bar && bar.contains(el));",
                element);
        assertThat(Boolean.TRUE.equals(inOverflowMenu)).as("Invitation sending section should be visible for future event (next week)").isTrue();
    }

    @Test
    void pastEventOtherSectionsRemainVisible() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Long eventId = createEventWithDate("Koncert wczoraj test", yesterday);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Verify that other sections are still visible (not affected by hiding invocation section)

        // "Zaproś" must remain available for past events; since Issue #226 it lives in the ⋮ overflow menu.
        By inviteSectionLocator = By.id("open-invite-btn");
        WebElement element = waitHelper().until(ExpectedConditions.presenceOfElementLocated(inviteSectionLocator));
        Object inOverflowMenu = ((JavascriptExecutor) driver).executeScript(
                "var el = arguments[0]; var bar = document.querySelector('.overflow-menu');" +
                " return !!(el && bar && bar.contains(el));",
                element);
        assertThat(Boolean.TRUE.equals(inOverflowMenu)).as("Invite participants action remains available (in overflow menu)").isTrue();

        // Event name should be displayed
        String title = driver.findElement(By.cssSelector("#events-content article header strong")).getText();
        assertThat(title).isEqualTo("Koncert wczoraj test");
    }
}
