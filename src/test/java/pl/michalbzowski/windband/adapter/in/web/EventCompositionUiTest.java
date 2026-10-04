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
 *       browser comes back to the panel showing it in order; XHR DELETE removes it
 *       (the remove route has no UI button yet, so it is driven directly);</li>
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
        wait.until(ExpectedConditions.urlContains("/events/" + eventId));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("event-setlist-panel"), "Marsz " + mark));

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Long.class, eventId, compositionId);
        assertThat(rowCount).isEqualTo(1L);

        Integer orderInSet = jdbcTemplate.queryForObject(
                "SELECT order_in_set FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Integer.class, eventId, compositionId);
        assertThat(orderInSet).isBetween(1, 100);

        // US-7.2 remove route — no UI button exists yet, so hit the endpoint directly.
        // Note the fetch-spec nuance: on a 3xx redirect only POST converts to GET;
        // a followed DELETE keeps its method and would re-hit /events/{id} (unmapped,
        // 500) — so use redirect:'manual': status 0 == "opaqueredirect", i.e. the route
        // answered with the 303 it is designed to answer with (pre-fix it answered 500).
        int deleteResult = fetchManual("DELETE", "/events/" + eventId + "/compositions/" + compositionId);
        assertThat(deleteResult).as("DELETE route returned a redirect (opaqueredirect)").isZero();

        Long afterDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_compositions WHERE event_id = ? AND composition_id = ?",
                Long.class, eventId, compositionId);
        assertThat(afterDelete).isZero();

        driver.navigate().refresh();
        WebElement panel = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-setlist-panel")));
        assertThat(panel.getText()).doesNotContain("Marsz " + mark);
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

    /** Same-origin fetch that does NOT follow redirects: 0 = 3xx received, 500 = server error. */
    private int fetchManual(String method, String path) {
        Object status = ((JavascriptExecutor) driver).executeAsyncScript(
                "var done = arguments[arguments.length - 1];"
                        + "fetch(arguments[0], {method: arguments[1], redirect: 'manual'})"
                        + ".then(function(response) { done(response.status); }, function(error) { done(-1); });",
                path, method);
        return ((Number) status).intValue();
    }
}
