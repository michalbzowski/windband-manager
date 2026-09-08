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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * t_7e21ac5b: the two legacy invite buttons ("Zaproś uczestników" / "Zaproś grupę") and their
 * server-rendered #invite-members-modal / #invite-group-modal dialogs have been replaced by a single
 * unified "Zaproś" button (#open-invite-btn) that opens the shared InvitationModal rendered at runtime.
 *
 * This test keeps the REGRESSION it was written for — after a quick-attendance HTMX reload of the
 * detail page, the invite button must still work (a DOMContentLoaded-only handler binding is lost on
 * re-render; the page now re-binds via dataset guard + MutationObserver). It asserts against the new
 * unified UI instead of the removed legacy ids: exactly one #open-invite-btn exists after the reload,
 * clicking it opens the shared modal (id=invitation-unified-modal), and it closes cleanly.
 */
class RehearsalInviteAfterQuickAttendanceUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void inviteButtonShouldWorkAfterQuickAttendance() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "InviteAfter" + uid;
        String lastName = "Test" + uid;

        // --- Create a member via UI ---
        createMember(firstName, lastName, wait);

        // --- Create a rehearsal via UI ---
        loginAndNavigateTo("/rehearsals");
        driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));
        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = arguments[0];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='endTime']\").value = '20:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala prób';",
                today);
        driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for the new rehearsal to be persisted in the DB
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName);

        assertThat(rehearsalId).isNotNull();
        assertThat(memberId).isNotNull();

        // --- Navigate to the new rehearsal ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Fresh rehearsal: empty attendance table (no auto-invite)
        assertThat(driver.findElements(By.cssSelector("#rehearsals-content tbody tr")).size())
                .as("Fresh rehearsal must show an empty attendance table (no auto-invite)")
                .isZero();

        // --- Invite the member explicitly so quick attendance has someone to walk through ---
        inviteMember(rehearsalId, memberId);

        // Reload to get the attendance row rendered
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select[data-member-id='" + memberId + "']")));

        // --- Now open quick attendance modal and complete it (under ⋮ overflow) ---
        clickOverflowInnerButton("quick-attendance-btn");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("quick-attendance-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === true;"));

        // Wait for progress to show
        WebDriverWait progressWait = new WebDriverWait(driver, Duration.ofSeconds(5));
        progressWait.until(d -> {
            String text = d.findElement(By.id("qa-progress")).getText();
            return text != null && text.startsWith("1 /");
        });

        // Click PRESENT for the member (only one member, so modal will close after)
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();

        // Wait for modal to close (which triggers the HTMX reload)
        WebDriverWait saveWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        saveWait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === false;"));

        // Wait for toast
        saveWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        // --- NOW THE REGRESSION: after the HTMX reload, exactly one unified invite
        // button must exist and still work (t_7e21ac5b replaced the two legacy buttons). ---
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));
        assertThat(driver.findElements(By.cssSelector(".rehearsal-invite-actions button")))
                .as("exactly one unified invite button in the invite section")
                .hasSize(1);
        // Legacy buttons / old server-rendered dialogs are gone (t_7e21ac5b).
        assertThat(driver.findElements(By.id("open-invite-modal-btn"))).isEmpty();
        assertThat(driver.findElements(By.id("open-invite-group-modal-btn"))).isEmpty();

        // --- Open the unified modal via the single button (click handler survived HTMX re-render) ---
        WebElement inviteBtn = driver.findElement(By.id("open-invite-btn"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", inviteBtn);

        // The shared modal dialog must appear and open (invite-options fetch + mount).
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invitation-unified-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invitation-unified-modal').open === true;"));

        assertThat((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('invitation-unified-modal').open === true;"))
                .as("Unified invite modal should open after quick attendance HTMX reload")
                .isTrue();

        // Close the modal and confirm it actually closed.
        ((JavascriptExecutor) driver).executeScript(
                "var dlg = document.getElementById('invitation-unified-modal');" +
                " if (dlg && typeof dlg.close === 'function') dlg.close();" +
                " else { var cb = dlg ? dlg.querySelector('[data-close]') : null; if (cb) cb.click(); }");
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return !document.getElementById('invitation-unified-modal') ||" +
                "       document.getElementById('invitation-unified-modal').open === false;"));
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) throws Exception {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));

        // Wait for the new member to be persisted in the DB
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM members WHERE first_name = ?", Long.class, firstName) > 0);
    }

    private void inviteMember(Long rehearsalId, Long memberId) {
        ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals/' + arguments[0] + '/invite', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({rehearsalId: arguments[0], memberId: arguments[1]}));" +
                "return xhr.status;", rehearsalId, memberId);
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
