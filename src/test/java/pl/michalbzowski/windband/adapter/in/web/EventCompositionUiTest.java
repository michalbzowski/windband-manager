package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.2 — "Skład repertuaru tego wydarzenia" panel on the event detail page.
 *
 * <p>Pins the full HTTP contract of the setlist routes, including the binding
 * defect found in production on 2026-10-04 (POST /events/{id}/compositions → 500):
 * both {@code assignComposition} and {@code unassignComposition} declared
 * {@code @PathVariable Long eventId} while the URI templates only carry
 * {@code {id}} / {@code {cid}}, so Spring could never resolve the path variable
 * ({@code MissingPathVariableException}). This class is the regression net:
 * <ol>
 *   <li>happy path — dialog add persists an {@code event_compositions} row and the
 *       browser comes back to the panel showing it in order; the US-7.2b per-row
 *       "Usuń" button + confirm modal removes it through the real UI;</li>
 *   <li>cross-band refusal — a composition of another band fails closed with 409
 *       and NO row is written (pre-fix the binding failure hid this entirely).</li>
 * </ol>
 *
 * <p>The former {@code @Disabled} reason ("{@code requireBandAccess} needs a real
 * band-scoped OidcUser") is obsolete: {@code TestSecurityConfig} (profile
 * {@code test}) already swaps the form-login principal for a {@code WindbandOidcUser}
 * bound to team 1 — the same mechanism {@code EventPartDeliveryUiTest} relies on.</p>
 */
class EventCompositionUiTest extends UiTestBase {

    private final String mark = "Us72" + System.nanoTime();

    @AfterEach
    void cleanupSetlistRows() {
        jdbcTemplate.update("DELETE FROM event_compositions WHERE composition_id IN "
                + "(SELECT id FROM compositions WHERE title LIKE ?)", "Marsz " + mark + "%");
        jdbcTemplate.update("DELETE FROM compositions WHERE title LIKE ?", "Marsz " + mark + "%");
        jdbcTemplate.update("DELETE FROM compositions WHERE title LIKE ?", "Utwór obcy " + mark + "%");
        jdbcTemplate.update("DELETE FROM band_events WHERE name LIKE ?", "Koncert " + mark + "%");
        jdbcTemplate.update("DELETE FROM bands WHERE name = ?", "Zesp Cross Test " + mark);
    }

    /** Happy path: the dialog add form persists and the panel shows the row; XHR DELETE removes it. */
    @Test
    void setlistDialog_addThenRemove_persistsAndClearsRow() {
        Long compositionId = seedComposition(1L, "Marsz " + mark);
        Long eventId = seedEvent(1L, "Koncert " + mark);

        loginAndNavigateTo("/events/" + eventId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-setlist-panel")));

        // Open the "+ Dodaj utwór" dialog and submit the picker with our composition.
        driver.findElement(By.id("add-setlist-composition-btn")).click();
        WebElement picker = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("setlist-composition-picker")));
        new Select(picker).selectByValue(String.valueOf(compositionId));
        driver.findElement(By.cssSelector("#add-composition-dialog button[type='submit']")).click();

        // Binding fixed: the POST 302s back and the setlist table now carries the row.
        // (Pre-fix this stalled here — the browser landed on a 500 error page.)
        // Text is polled through a stale-safe reader: EC#textToBePresent* dies on the
        // "Node does not belong to the document" inspector error Chrome throws while the
        // POST navigation swaps the DOM mid-poll (CI race seen on run 37229913645).
        wait.until(ExpectedConditions.urlContains("/events/" + eventId));
        wait.until(d -> panelText().contains("Marsz " + mark));

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Long.class, eventId, compositionId);
        assertThat(rowCount).isEqualTo(1L);

        Integer orderInSet = jdbcTemplate.queryForObject(
                "SELECT order_in_set FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Integer.class, eventId, compositionId);
        assertThat(orderInSet).isBetween(1, 100);

        // US-7.2b — the per-row "Usuń" button + confirm modal now drive the DELETE
        // route through the real UI (fetch DELETE → controller 302 → JS reload). Poll
        // the panel text through the stale-safe reader because the reload swaps the
        // DOM mid-navigation (same Chrome inspector race as the add flow above).
        WebElement removeBtn = driver.findElement(By.cssSelector("[data-remove-setlist-btn]"));
        assertThat(removeBtn.getAttribute("data-cid")).isEqualTo(String.valueOf(compositionId));
        removeBtn.click();
        WebElement confirmBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("remove-setlist-confirm-btn")));
        confirmBtn.click();
        wait.until(d -> panelText().length() > 0);
        wait.until(d -> !panelText().contains("Marsz " + mark));

        Long afterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Long.class, eventId, compositionId);
        assertThat(afterDelete).isZero();
    }

    /** Cross-band refusal: a foreign band's composition cannot be linked (409 fail-closed, no row). */
    @Test
    void setlistAssign_crossBandComposition_rejectedWithNoRow() {
        jdbcTemplate.update("INSERT INTO bands (name, slug, created_at) VALUES (?, ?, NOW())",
                "Zesp Cross Test " + mark, "cross-test-" + mark);
        Long otherBandId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM bands WHERE name = ?", Long.class, "Zesp Cross Test " + mark);
        Long foreignCompositionId = seedComposition(otherBandId, "Utwór obcy " + mark);
        Long eventId = seedEvent(1L, "Koncert " + mark);

        loginAndNavigateTo("/events/" + eventId);

        // Pre-fix this returned 500 (the binding failure hid the service refusal entirely).
        int status = xhr("POST", "/events/" + eventId + "/compositions",
                "compositionId=" + foreignCompositionId);
        assertThat(status).isEqualTo(409);

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Long.class, eventId, foreignCompositionId);
        assertThat(rowCount).isZero();
    }

    // ---- seed helpers (direct SQL keeps fixtures uniquely named per run) ----

    private Long seedComposition(long bandId, String title) {
        jdbcTemplate.update(
                "INSERT INTO compositions (band_id, title, composer, arranger, description, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'Jan', 'Anna', '', 'DRAFT', NOW(), NOW())",
                bandId, title);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM compositions WHERE title = ? AND band_id = ?",
                Long.class, title, bandId);
    }

    private Long seedEvent(long bandId, String name) {
        jdbcTemplate.update(
                "INSERT INTO band_events (band_id, name, date, start_time, location, event_type, payment_type) "
                        + "VALUES (?, ?, CURRENT_DATE + 30, '18:00'::TIME, 'Rynek', 'CONCERT', 'FREE')",
                bandId, name);
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class, name);
    }

    /** Synchronous same-origin XHR — session cookie rides along; returns the final status code. */
    private int xhr(String method, String path, String body) {
        Object status = ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open(arguments[0], arguments[1], false);"
                        + "if (arguments[2]) { xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded'); }"
                        + "xhr.send(arguments[2] || null);"
                        + "return xhr.status;",
                method, path, body);
        return ((Number) status).intValue();
    }

    /** Stale-safe panel text: re-locates the node each poll and swallows navigation-time DOM swaps. */
    private String panelText() {
        try {
            return driver.findElement(By.id("event-setlist-panel")).getText();
        } catch (org.openqa.selenium.WebDriverException race) {
            // StaleElementReferenceException and the "node does not belong to the document"
            // inspector error both land here while the POST navigation swaps the DOM.
            return "";
        }
    }
}
