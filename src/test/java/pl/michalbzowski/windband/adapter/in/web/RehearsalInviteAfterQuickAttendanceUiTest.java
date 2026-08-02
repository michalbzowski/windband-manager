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
 * Verifies that the "Zaproś uczestników" and "Zaproś grupę" buttons still work
 * after doing quick attendance (which triggers an HTMX reload of the detail page).
 *
 * The bug: After quick attendance completes, the detail page is reloaded via HTMX
 * (htmx.ajax GET /rehearsals/{id}). This does NOT fire DOMContentLoaded, so the
 * click handlers for open-invite-modal-btn and open-invite-group-modal-btn
 * are not re-attached. Clicking them does nothing.
 */
class RehearsalInviteAfterQuickAttendanceUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void inviteButtonsShouldWorkAfterQuickAttendance() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "InviteAfter" + uid;
        String lastName = "Test" + uid;

        // --- Create a member via UI ---
        createMember(firstName, lastName, wait);

        // --- Create a rehearsal via UI ---
        loginAndNavigateTo("/rehearsals");
        driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]")).click();
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

        // --- Now open quick attendance modal and complete it ---
        // This will trigger an HTMX reload at the end
        driver.findElement(By.id("quick-attendance-btn")).click();
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
        String beforeClick = driver.findElement(By.id("qa-progress")).getText();
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();

        // Wait for modal to close (which triggers the HTMX reload)
        WebDriverWait saveWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        saveWait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === false;"));

        // Wait for toast
        saveWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        // --- NOW THE BUG: After HTMX reload, the invite buttons should still work ---
        // Wait for the detail page to be reloaded (HTMX swap completes)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));

        // --- TRY TO OPEN INVITE MEMBER MODAL ---
        // This should work but currently doesn't because click handlers aren't re-attached
        WebElement inviteBtn = driver.findElement(By.id("open-invite-modal-btn"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", inviteBtn);

        // Wait for modal to open - this will fail with the bug
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-members-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-members-modal').open === true;"));

        // Verify modal is open
        assertThat((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('invite-members-modal').open === true;"))
                .as("Invite members modal should open after quick attendance HTMX reload")
                .isTrue();

        // Close modal
        WebElement closeBtn = driver.findElement(By.cssSelector("#invite-members-modal [data-close]"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", closeBtn);
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-members-modal').open === false;"));

        // --- TRY TO OPEN INVITE GROUP MODAL ---
        WebElement inviteGroupBtn = driver.findElement(By.id("open-invite-group-modal-btn"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", inviteGroupBtn);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-group-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-group-modal').open === true;"));

        assertThat((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('invite-group-modal').open === true;"))
                .as("Invite group modal should open after quick attendance HTMX reload")
                .isTrue();
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) throws Exception {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
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