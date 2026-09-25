package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for Issue #226 - "Przenies akcje" on the event detail view:
 * the "Zaprosz" (open-invite) and "Wyslij do wszystkich" (send-all) actions must
 * live inside the three-dot overflow menu of the unified page header, not as
 * stand-alone floating sections in the body.
 *
 * Covered:
 *  - future event: BOTH actions are inside the overflow menu and clickable from
 *    it (Zaprosz opens the shared InvitationModal); no floating copies remain;
 *  - past event: "send-all" must NOT be rendered at all (Issue #118 contract)
 *    while "open-invite" stays available in the menu.
 *
 * Note: the buttons keep their original ids (#open-invite-btn / #send-all-btn),
 * so all existing JS bindings (windband-utils.js send-all handler, unified invite
 * binder) keep working unchanged after the relocation into .overflow-menu.
 */
class EventDetailActionsInOverflowMenuUiTest extends UiTestBase {

    private WebDriverWait waitHelper() {
        return new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    /** Create a band event with an explicit date. */
    private Long createEventWithDate(String name, LocalDate date) {
        String sql = "INSERT INTO band_events (name, date, start_time, location, event_type,"
                + " payment_type, payment_amount, notes, band_id)"
                + " VALUES (?, ?, '18:00', 'Test Location', 'CONCERT', 'FREE', 0, 'issue #226 regression', 1)";
        org.springframework.jdbc.support.GeneratedKeyHolder kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, date.toString());
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    /** The three-dot trigger of the unified page header bar. */
    private WebElement menuTrigger() {
        return driver.findElement(By.cssSelector(
                "nav.detail-actions-bar[data-detail-bar] [data-detail-action='toggle-more']"));
    }

    /** Open (or keep open) the overflow menu and wait until it is visible. */
    private void openOverflowMenu() {
        boolean alreadyOpen = "true".equals(menuTrigger().getAttribute("aria-expanded"));
        if (!alreadyOpen) {
            jsClick(menuTrigger());
        }
        waitHelper().until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("nav.detail-actions-bar[data-detail-bar] .overflow-menu")));
    }

    private long countMatching(String css) {
        Long n = (Long) ((JavascriptExecutor) driver).executeScript(
                "return document.querySelectorAll('" + css + "').length;");
        return n == null ? 0 : n;
    }

    @Test
    void futureEvent_bothActionsLiveInsideOverflowMenu() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Long eventId = createEventWithDate("Koncert przyszly #226", tomorrow);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        // Page scripts relocate the actions; wait until they are actually in the menu.
        waitHelper().until(d -> countMatching("nav[data-detail-bar] .overflow-menu #open-invite-btn") == 1);

        assertThat(countMatching("nav[data-detail-bar] .overflow-menu #open-invite-btn"))
                .as("open-invite should be inside the three-dot menu")
                .isEqualTo(1);
        assertThat(countMatching("nav[data-detail-bar] .overflow-menu #send-all-btn"))
                .as("send-all should be inside the three-dot menu")
                .isEqualTo(1);

        // No floating copies may remain in the body content (old layout is gone).
        assertThat(countMatching("#events-content #open-invite-btn")).isZero();
        assertThat(countMatching("#events-content #send-all-btn")).isZero();

        // Both actions are reachable: opening the menu exposes them for display.
        openOverflowMenu();
        assertThat(driver.findElement(By.cssSelector("nav[data-detail-bar] .overflow-menu #open-invite-btn"))
                .isDisplayed()).as("open-invite visible inside the open menu").isTrue();
        assertThat(driver.findElement(By.cssSelector("nav[data-detail-bar] .overflow-menu #send-all-btn"))
                .isDisplayed()).as("send-all visible inside the open menu").isTrue();
    }

    @Test
    void futureEvent_inviteWorksFromOverflowMenu() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Long eventId = createEventWithDate("Koncert modal #226", tomorrow);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        waitHelper().until(d -> countMatching("nav[data-detail-bar] .overflow-menu #open-invite-btn") == 1);

        openOverflowMenu();
        jsClick(driver.findElement(By.cssSelector("nav[data-detail-bar] .overflow-menu #open-invite-btn")));

        // The shared InvitationModal opens (rows rendered from /api/events/{id}/invite-options).
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));

        ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('invitation-unified-modal'); if (d && d.close) d.close();");
    }

    @Test
    void pastEvent_sendAllNotInMenu_inviteStays() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        Long eventId = createEventWithDate("Koncert wczoraj #226", yesterday);

        loginAndNavigateTo("/events/" + eventId);
        waitHelper().until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        try {
            waitHelper().until(d -> countMatching("#open-invite-btn") >= 1);
        } catch (TimeoutException ignored) {
            // asserted below either way
        }

        // send-all must NOT be rendered at all (Issue #118 contract).
        assertThat(countMatching("#send-all-btn")).isZero();

        // open-invite stays available, inside the menu.
        assertThat(countMatching("nav[data-detail-bar] .overflow-menu #open-invite-btn"))
                .as("open-invite should still be in the three-dot menu for a past event")
                .isEqualTo(1);

        openOverflowMenu();
        assertThat(driver.findElement(By.cssSelector("nav[data-detail-bar] .overflow-menu #open-invite-btn"))
                .isDisplayed()).isTrue();
        assertThat(countMatching("nav[data-detail-bar] .overflow-menu #send-all-btn")).isZero();
    }
}
