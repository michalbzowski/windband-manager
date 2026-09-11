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
 * t_5c77c4cb: the rehearsal detail page now uses a single unified invite
 * modal ({@code #open-invite-btn} → {@code window.InvitationModal}) instead of
 * the two separate "Zaproś uczestników" / "Zaproś grupę" dialogs that existed
 * before this branch.
 *
 * <p>Regression: after quick attendance completes, HTMX reloads
 * {@code #rehearsals-content} via an outerHTML swap. The unified modal is
 * initialised at DOMContentLoaded and its trigger lives inside the swapped
 * container, so we need to confirm the click handler is re-attached after the
 * swap. This test exercises that exactly:
 * <ol>
 *   <li>Create a member + rehearsal via UI.</li>
 *   <li>Invite the member explicitly (attendance row appears).</li>
 *   <li>Run quick attendance for that member (triggers the HTMX reload).</li>
 *   <li>Click the single "Zaproś" button — the unified modal must open and be
 *       functional (a group or member row should render).</li>
 * </ol>
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

        // --- NOW THE REGRESSION: After HTMX reload, the invite button should still work ---
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Click the single unified invite button — it must open the modal.
        WebElement inviteBtn = driver.findElement(By.id("open-invite-btn"));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", inviteBtn);

        // The unified modal renders its selection rows asynchronously.
        // Wait for at least one row (group or member) to appear.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));

        // The confirm button should exist (enabled or disabled — existence is the assertion).
        assertThat(driver.findElements(By.cssSelector(".invitation-confirm")).size())
                .as("The unified invite modal's confirm button must be present after quick-attendance HTMX reload")
                .isEqualTo(1);

        System.out.println("[TEST] PASS: unified invite modal opens and renders rows after quick-attendance");
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) throws Exception {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
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
