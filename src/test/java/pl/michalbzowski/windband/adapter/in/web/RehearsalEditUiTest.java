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
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for the "Edytuj spotkanie" (edit rehearsal) flow.
 *
 * Owner-reported bug: "Chcę edytować spotkanie. Zmieniam godzinę spotkania.
 * Zapisuje. Nic się nie zapisało a zostałem przeniesiony na listę spotkań."
 *
 * The edit form on {@code /rehearsals/{id}/edit} sends a PUT to
 * {@code /api/rehearsals/{id}} with the form fields serialized as JSON.
 * If the time change does not survive a page reload, the persistence layer
 * is broken — either the PUT is not reaching the server, the server is
 * rejecting the body, or the entity is not actually being mutated.
 *
 * These tests assert the FULL round trip: type a new time in the edit form,
 * click "Zapisz zmiany", and confirm via a direct database read (jdbcTemplate
 * is the most reliable signal — see the skill note on
 * `event_participations`/`event_attendances` JDBC poll being "the assertion
 * of last resort") that the new start_time is persisted.
 */
public class RehearsalEditUiTest extends UiTestBase {

    /**
     * Core regression: change startTime on an existing rehearsal via the
     * edit form, save, and verify the new time is in the database.
     */
    @Test
    public void editingRehearsalStartTime_persistsTheNewValue() {
        // 1. Log in (any URL works — we just need the session cookie set so
        // the XHR POST against /api/rehearsals is authenticated). The
        // loginAndNavigateTo helper logs in as admin and lands on /.
        loginAndNavigateTo("/");

        // 2. Create a rehearsal via the API now that we are authenticated.
        long uid = System.nanoTime();
        String name = "EditMe " + uid;
        Long rehearsalId = createRehearsalViaApi(name, "2026-12-31", "18:00", "20:00", "Stara Sala");
        assertThat(rehearsalId).as("rehearsal should be created via API").isNotNull();

        // 3. Open the edit page.
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId + "/edit");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsal-edit-form")));

        // 3. Read the original start time to make sure we are actually changing it.
        WebElement startTimeInput = driver.findElement(
                By.cssSelector("input[name='startTime']"));
        String originalStart = startTimeInput.getAttribute("value");
        assertThat(originalStart)
                .as("edit form should pre-populate the existing start time")
                .isEqualTo("18:00");

        // 4. Type a new time.  Skill rule: sendKeys on <input type="time"> is
        // unreliable in headless Chrome; set the value via JS instead, then
        // dispatch the 'input' event so any listeners see the new value.
        ((JavascriptExecutor) driver).executeScript(
                "var el = document.querySelector(\"input[name='startTime']\");" +
                "el.value = '20:30';" +
                "el.dispatchEvent(new Event('input', {bubbles: true}));" +
                "el.dispatchEvent(new Event('change', {bubbles: true}));",
                new Object[]{});

        // 5. Click "Zapisz zmiany".
        WebElement saveButton = driver.findElement(
                By.xpath("//button[contains(text(), 'Zapisz zmiany')]"));
        saveButton.click();

        // 6. After successful save the inline JS handler redirects to the
        // detail page. Wait for the URL to leave the edit route and land on
        // /rehearsals/{id} (not the list /rehearsals).
        wait.until(d -> d.getCurrentUrl().matches(".*/rehearsals/\\d+(?!.*/edit).*"));
        assertThat(driver.getCurrentUrl())
                .as("after save we should land on the detail page, not the list")
                .matches(".*/rehearsals/\\d+(?!.*/edit).*")
                .doesNotMatch(".*/rehearsals$");

        // 7. The authoritative persistence check: ask the database.
        // Do NOT re-render the page and parse HTML — the skill notes
        // that a stale HTMX fragment can hide the real value on slow CI.
        java.time.LocalTime persistedStart = jdbcTemplate.queryForObject(
                "SELECT start_time FROM rehearsals WHERE id = ?",
                java.sql.Time.class, rehearsalId)
                .toLocalTime();
        assertThat(persistedStart)
                .as("the new start time should be persisted in the database")
                .isEqualTo(LocalTime.of(20, 30));
    }

    /**
     * Regression for the related issue: changing endTime should also persist.
     */
    @Test
    public void editingRehearsalEndTime_persistsTheNewValue() {
        loginAndNavigateTo("/");

        long uid = System.nanoTime();
        Long rehearsalId = createRehearsalViaApi("EditMe2 " + uid, "2026-12-30", "17:00", "19:00", "Sala X");
        assertThat(rehearsalId).isNotNull();

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId + "/edit");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsal-edit-form")));

        ((JavascriptExecutor) driver).executeScript(
                "var el = document.querySelector(\"input[name='endTime']\");" +
                "el.value = '21:15';" +
                "el.dispatchEvent(new Event('input', {bubbles: true}));" +
                "el.dispatchEvent(new Event('change', {bubbles: true}));",
                new Object[]{});

        driver.findElement(By.xpath("//button[contains(text(), 'Zapisz zmiany')]")).click();

        wait.until(d -> d.getCurrentUrl().matches(".*/rehearsals/\\d+(?!.*/edit).*"));

        java.time.LocalTime persistedEnd = jdbcTemplate.queryForObject(
                "SELECT end_time FROM rehearsals WHERE id = ?",
                java.sql.Time.class, rehearsalId)
                .toLocalTime();
        assertThat(persistedEnd)
                .as("the new end time should be persisted in the database")
                .isEqualTo(LocalTime.of(21, 15));
    }

    /**
     * Regression for the related issue: changing location should also persist.
     * (The original owner report focused on time but the same code path
     * serializes all fields — if one field works and another does not, the
     * test surfaces that immediately.)
     */
    @Test
    public void editingRehearsalLocation_persistsTheNewValue() {
        loginAndNavigateTo("/");

        long uid = System.nanoTime();
        Long rehearsalId = createRehearsalViaApi("EditMe3 " + uid, "2026-12-29", "10:00", null, "Stara lokalizacja");
        assertThat(rehearsalId).isNotNull();

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId + "/edit");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsal-edit-form")));

        WebElement locationInput = driver.findElement(
                By.cssSelector("input[name='location']"));
        locationInput.clear();
        locationInput.sendKeys("Nowa lokalizacja " + uid);

        driver.findElement(By.xpath("//button[contains(text(), 'Zapisz zmiany')]")).click();

        wait.until(d -> d.getCurrentUrl().matches(".*/rehearsals/\\d+(?!.*/edit).*"));

        String persistedLocation = jdbcTemplate.queryForObject(
                "SELECT location FROM rehearsals WHERE id = ?",
                String.class, rehearsalId);
        assertThat(persistedLocation)
                .as("the new location should be persisted in the database")
                .isEqualTo("Nowa lokalizacja " + uid);
    }

    /**
     * Regression: changing the date should also persist. The original
     * {@code RehearsalCommandService.updateRehearsal} only called
     * {@code updateTime} / {@code updateLocation} / {@code updateNotes},
     * silently ignoring the date field — this test fails until a
     * {@code Rehearsal.updateDate(...)} method is added and called from
     * the command service.
     */
    @Test
    public void editingRehearsalDate_persistsTheNewValue() {
        loginAndNavigateTo("/");

        long uid = System.nanoTime();
        Long rehearsalId = createRehearsalViaApi("EditMe4 " + uid, "2026-09-15", "12:00", null, "Sala D");
        assertThat(rehearsalId).isNotNull();

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId + "/edit");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsal-edit-form")));

        // JS-set the date (skill: sendKeys on <input type="date"> is unreliable
        // in headless Chrome).
        ((JavascriptExecutor) driver).executeScript(
                "var el = document.querySelector(\"input[name='date']\");" +
                "el.value = '2027-01-20';" +
                "el.dispatchEvent(new Event('input', {bubbles: true}));" +
                "el.dispatchEvent(new Event('change', {bubbles: true}));",
                new Object[]{});

        driver.findElement(By.xpath("//button[contains(text(), 'Zapisz zmiany')]")).click();

        wait.until(d -> d.getCurrentUrl().matches(".*/rehearsals/\\d+(?!.*/edit).*"));

        java.time.LocalDate persistedDate = jdbcTemplate.queryForObject(
                "SELECT date FROM rehearsals WHERE id = ?",
                java.sql.Date.class, rehearsalId)
                .toLocalDate();
        assertThat(persistedDate)
                .as("the new date should be persisted in the database")
                .isEqualTo(LocalDate.of(2027, 1, 20));
    }

    /**
     * Helper: create a rehearsal via the REST API. Returns the new id.
     * Uses synchronous XHR (skill: "Selenium `executeScript` does NOT await
     * Promises — for in-page API calls from a test, use synchronous
     * XMLHttpRequest"). Must be called AFTER the driver has been
     * authenticated (loginAndNavigateTo).
     */
    private Long createRehearsalViaApi(String name, String date, String startTime,
                                       String endTime, String location) {
        String notesMarker = "test-notes-" + System.nanoTime();
        String body = "{"
                + "\"name\":\"" + name + "\","
                + "\"date\":\"" + date + "\","
                + "\"startTime\":\"" + startTime + "\","
                + (endTime == null ? "\"endTime\":null," : "\"endTime\":\"" + endTime + "\",")
                + "\"location\":\"" + location + "\","
                + "\"notes\":\"" + notesMarker + "\""
                + "}";
        String responseBody = (String) ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "xhr.send(arguments[0]);" +
                "if (xhr.status !== 201) { throw new Error('create rehearsal failed: ' + xhr.status + ' ' + xhr.responseText); }" +
                "return xhr.responseText;",
                body);
        // The response body is a JSON Rehearsal. The id field is a number;
        // we extract it with a small regex to avoid pulling in a JSON parser.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*(\\d+)").matcher(responseBody);
        if (!m.find()) {
            throw new IllegalStateException("createRehearsalViaApi: could not parse id from response: " + responseBody);
        }
        return Long.valueOf(m.group(1));
    }
}
