package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that changing a member's attendance status in the dropdown
 * auto-saves to the database and shows a success toast.
 *
 * <p>Previously the detail page had a "Zapisz obecność" button that the
 * admin clicked after editing each select. Now the {@code change} event on
 * any {@code .status-select} (delegated in {@code windband-utils.js}) sends
 * the new status to {@code /api/rehearsals/{id}/attendance} immediately,
 * so the page no longer needs a manual save step.</p>
 */
class RehearsalAttendanceToastUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void changingStatusSelect_autoSavesAndShowsSuccessToast() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Toast" + uid;
        String lastName = "Test" + uid;

        // --- Create a member via UI ---
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
        // Wait for the new member to be persisted in the DB before we read MAX(id) below
        // (replaces fixed Thread.sleep — polls DB until row appears)
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM members WHERE first_name = ?", Long.class, firstName) > 0);

        // --- Create a rehearsal via UI ---
        loginAndNavigateTo("/rehearsals");
        driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");
        driver.findElement(By.cssSelector("input[name='startTime']")).sendKeys("18:00");
        driver.findElement(By.cssSelector("input[name='endTime']")).sendKeys("20:00");
        driver.findElement(By.cssSelector("input[name='location']")).sendKeys("Sala prób");
        driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));
        // Wait for the new rehearsal to be persisted in the DB before we read MAX(id) below
        // (replaces fixed Thread.sleep — polls DB until row appears)
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        // --- Navigate directly to the new rehearsal by id (avoids stale-row trap:
        // clicking "Szczegóły" of the first row may open a different, older
        // rehearsal that still has attendance rows from previous test runs) ---
        String rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today).toString();
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName);

        // Fresh rehearsal: no rows. Invite the just-created member via the API so
        // the attendance table has a row to mark PRESENT on.
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        assertThat(driver.findElements(By.cssSelector("#rehearsals-content tbody tr")).size())
                .as("Fresh rehearsal must show an empty attendance table (no auto-invite)")
                .isZero();
        ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals/' + arguments[0] + '/invite', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({rehearsalId: arguments[0], memberId: arguments[1]}));" +
                "return xhr.status;", rehearsalId, String.valueOf(memberId));

        // Reload the detail page so the full <script> bundle (with the delegated
        // .status-select change listener) is loaded.
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));

        // The "Zapisz obecność" button must be gone — auto-save replaces it.
        assertThat(driver.findElements(By.id("save-attendance-btn")))
                .as("'Zapisz obecność' button should be removed; attendance auto-saves on change")
                .isEmpty();

        // --- Change the member's status to PRESENT and dispatch 'change' ---
        // Selenium's native <select>.click() doesn't fire the browser 'change'
        // event reliably in headless Chrome — set the value and dispatch the
        // event explicitly. The delegated listener in windband-utils.js picks it
        // up and posts to /api/rehearsals/{id}/attendance.
        WebElement select = driver.findElement(By.cssSelector("#rehearsals-content .status-select"));
        ((JavascriptExecutor) driver).executeScript(
                "var s = arguments[0]; s.value = 'PRESENT'; s.dispatchEvent(new Event('change', {bubbles:true}));",
                select);

        // --- Assert a success toast appears (auto-save fired) ---
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("toast-container")));
        WebDriverWait toastWait = new WebDriverWait(driver, Duration.ofSeconds(8));
        toastWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        String toastText = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('toast-container').textContent;");
        System.out.println("[TEST] toast-container text: '" + toastText + "'");
        assertThat(toastText)
                .as("Changing a status select must auto-save and show a success toast")
                .contains("Zapisano obecność");

        // --- Assert the change is persisted in the database ---
        String persistedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM attendances WHERE rehearsal_id = ? AND member_id = ?",
                String.class, Long.valueOf(rehearsalId), memberId);
        System.out.println("[TEST] persisted status: " + persistedStatus);
        assertThat(persistedStatus)
                .as("Status change must be persisted in the DB without a manual save step")
                .isEqualTo("PRESENT");
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
