package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * t_5c77c4cb: the rehearsal detail page now uses the shared unified
 * {@code window.InvitationModal} component (same as the event page, since
 * t_c9b13437). This test verifies:
 * <ol>
 *   <li>A single "Zaproś" button exists and opens the union modal.</li>
 *   <li>Selecting a member row and clicking "Potwierdź i dodaj" creates an
 *       {@code Attendance} row with status NO_RESPONSE in the database.</li>
 * </ol>
 */
class RehearsalInviteModalUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void unifiedInviteAddsMemberToRehearsal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Inv" + uid;
        String lastName = "Test" + uid;

        // --- Create a member via UI ---
        createMember(firstName, lastName, wait);

        Long memberId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName);
        assertThat(memberId).isNotNull();

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

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);
            assertThat(id).isNotNull();
        });

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);
        System.out.println("[TEST] rehearsalId=" + rehearsalId + " memberId=" + memberId);

        // --- Navigate to the rehearsal detail page ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsals-content")));

        // The single unified button should be present.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Click the button to open the unified invite modal.
        jsClick(driver.findElement(By.id("open-invite-btn")));

        // Wait for at least one invitation row (member or group) to render.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));

        // Find our member row by data-id and text label, then click it.
        String memberRowId = wait.until(d -> d.findElements(By.cssSelector(".invitation-row[data-kind='member']")).stream()
                .filter(r -> r.getText().contains(firstName))
                .map(r -> r.getAttribute("data-id"))
                .filter(s -> s != null && !s.isEmpty())
                .findFirst().orElse(null));
        assertThat(memberRowId).as("member row for '%s' should exist in the modal", firstName).isNotNull();

        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-row[data-kind='member'][data-id='" + memberRowId + "']\").click();");

        // Click "Potwierdź i dodaj" — wait until it is enabled (selection count > 0).
        wait.until(d -> d.findElements(By.cssSelector(".invitation-confirm")).stream()
                .filter(WebElement::isEnabled).findFirst().orElse(null) != null);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-confirm\").click();");

        // Wait for the Attendance row to appear in the database.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attendances WHERE rehearsal_id = ? AND member_id = ?",
                    Integer.class, rehearsalId, memberId);
            assertThat(count).isGreaterThan(0);

            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM attendances WHERE rehearsal_id = ? AND member_id = ?",
                    String.class, rehearsalId, memberId);
            assertThat(status).isEqualTo("NO_RESPONSE");
        });

        // The attendance table should now show the member with a status select.
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select[data-member-id='" + memberId + "']")));
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
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}
