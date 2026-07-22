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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the quick-attendance modal opened from the rehearsal detail view:
 * one member at a time, status buttons, save-then-advance, back button,
 * auto-close after the last member, and persistence across reload.
 *
 * Resilient to the number of active members in the team (does not assume a
 * fixed count — other tests may add members before this one runs).
 */
class QuickAttendanceModalUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void quickAttendanceModal_savesAndAdvancesAndPersists() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName1 = "Quick" + uid;
        String firstName2 = "Quick2" + uid;

        // --- Create two members via UI ---
        createMember(firstName1, "Test" + uid);
        createMember(firstName2, "Test" + uid);

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
        Thread.sleep(1000);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);
        Long memberId1 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName1);
        Long memberId2 = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ? ORDER BY id ASC LIMIT 1", Long.class, firstName2);
        assertThat(rehearsalId).isNotNull();
        assertThat(memberId1).isNotNull();
        assertThat(memberId2).isNotNull();

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        // Fresh rehearsal: empty attendance table (no rows yet). We MUST invite first
        // so the quick-attendance modal has at least one row to walk through.
        assertThat(driver.findElements(By.cssSelector("#rehearsals-content tbody tr")).size())
                .as("Fresh rehearsal must show an empty attendance table (no auto-invite)")
                .isZero();
        // Invite both members explicitly — the modal needs at least 2 to test save-then-advance.
        inviteMember(rehearsalId, memberId1);
        inviteMember(rehearsalId, memberId2);
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select[data-member-id='" + memberId1 + "']")));

        int memberCount = driver.findElements(By.cssSelector("#rehearsals-content tbody tr")).size();
        assertThat(memberCount)
                .as("Detail should show only the 2 explicitly invited members, not every active member")
                .isEqualTo(2);
        System.out.println("[TEST] memberCount=" + memberCount);

        // --- Open quick attendance modal ---
        driver.findElement(By.id("quick-attendance-btn")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("quick-attendance-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === true;"));
        assertThat(isModalOpen()).isTrue();

        // progress shows 1 / N — wait for the JS handler to set it before reading.
        // (quickAttendanceRender sets the textContent synchronously inside the
        // openQuickAttendance click handler, but with 2 members the modal can
        // sometimes be polled open before the textContent update lands.)
        WebDriverWait progressWait = new WebDriverWait(driver, Duration.ofSeconds(5));
        progressWait.until(d -> {
            String text = d.findElement(By.id("qa-progress")).getText();
            return text != null && text.startsWith("1 /");
        });
        String progress1 = driver.findElement(By.id("qa-progress")).getText();
        System.out.println("[TEST] progress after open: " + progress1);
        assertThat(progress1).startsWith("1 /");

        // --- Member 1: click PRESENT -> should advance to 2 / N ---
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
        Thread.sleep(600);
        String progress2 = driver.findElement(By.id("qa-progress")).getText();
        System.out.println("[TEST] progress after first save: " + progress2);
        assertThat(progress2).startsWith("2 /");

        // --- Back button: returns to 1 / N ---
        WebElement backBtn = driver.findElement(By.id("qa-back"));
        backBtn.click();
        Thread.sleep(600);
        String afterBack = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('qa-progress').textContent;");
        System.out.println("[TEST] after back: progress=" + afterBack);
        assertThat(afterBack).startsWith("1 /");
        assertThat(driver.findElement(By.id("qa-back")).getAttribute("disabled")).isNotNull();

        // --- Click through the rest (PRESENT for every remaining member) until modal closes ---
        // Wait for the save response (progress advances or modal closes) after each click
        // to avoid racing the next click against a slow fetch under suite load.
        WebDriverWait saveWait = new WebDriverWait(driver, Duration.ofSeconds(20));
        int maxClicks = memberCount + 2;
        int clicks = 0;
        while (isModalOpen() && clicks < maxClicks) {
            String before = driver.findElement(By.id("qa-progress")).getText();
            driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
            final int clickNo = clicks;
            saveWait.until(d -> {
                boolean closed = !(Boolean) ((JavascriptExecutor) d).executeScript(
                        "return document.getElementById('quick-attendance-modal').open === true;");
                if (closed) return true;
                String now = d.findElement(By.id("qa-progress")).getText();
                return !now.equals(before);
            });
            clicks++;
            System.out.println("[TEST] click " + clickNo + " done, modalOpen=" + isModalOpen());
        }
        System.out.println("[TEST] total clicks to finish: " + clicks);
        saveWait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === false;"));
        assertThat(isModalOpen()).isFalse();
        saveWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        // --- Reload and assert all members persisted as PRESENT ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));
        List<WebElement> selects = driver.findElements(By.cssSelector("#rehearsals-content .status-select"));
        for (WebElement sel : selects) {
            String val = (String) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].value;", sel);
            assertThat(val).as("Member status should persist as PRESENT after quick attendance").isEqualTo("PRESENT");
        }
        System.out.println("[TEST] all " + selects.size() + " members persisted as PRESENT");
    }

    private boolean isModalOpen() {
        return (Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('quick-attendance-modal').open === true;");
    }

    private void createMember(String firstName, String lastName) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
        Thread.sleep(1000);
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
