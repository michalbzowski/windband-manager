package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;

/**
 * UI tests for filtering participants on the rehearsal detail page.
 * Tests text filtering (first name, last name) and attendance status filtering.
 */
class RehearsalDetailFilterUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void textFilterShouldFilterByFirstName() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "FilterFirst" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName, lastName, wait);
        createMember("OtherFirst" + uid, "OtherLast" + uid, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        Long memberId1 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName);

        Long memberId2 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "OtherFirst" + uid);

        assertThat(rehearsalId).isNotNull();
        assertThat(memberId1).isNotNull();
        assertThat(memberId2).isNotNull();

        // --- Invite both members to the rehearsal ---
        inviteMemberToRehearsal(rehearsalId, memberId1);
        inviteMemberToRehearsal(rehearsalId, memberId2);

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Test text filter by first name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("attendance-filter")));
        filterInput.clear();
        filterInput.sendKeys(firstName);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);
        assertThat(visibleRows.get(0).findElement(By.cssSelector("td:first-child")).getText())
                .contains(firstName);
    }

    @Test
    void textFilterShouldFilterByLastName() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Test" + uid;
        String lastName = "FilterLast" + uid;

        // --- Create members via UI ---
        createMember(firstName, lastName, wait);
        createMember("Test" + uid, "OtherLast" + uid, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        Long memberId1 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE last_name = ?", Long.class, lastName);

        Long memberId2 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE last_name = ?", Long.class, "OtherLast" + uid);

        assertThat(rehearsalId).isNotNull();
        assertThat(memberId1).isNotNull();
        assertThat(memberId2).isNotNull();

        // --- Invite both members to the rehearsal ---
        inviteMemberToRehearsal(rehearsalId, memberId1);
        inviteMemberToRehearsal(rehearsalId, memberId2);

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Test text filter by last name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("attendance-filter")));
        filterInput.clear();
        filterInput.sendKeys(lastName);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);
        assertThat(visibleRows.get(0).findElement(By.cssSelector("td:first-child")).getText())
                .contains(lastName);
    }

    @Test
    void attendanceFilterShouldFilterByPresent() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "AttFilter" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName, wait);
        createMember(firstName + "2", lastName, wait);
        createMember(firstName + "3", lastName, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        assertThat(rehearsalId).isNotNull();
        assertThat(memberIds).hasSize(3);

        // --- Invite all three members to the rehearsal ---
        for (Long memberId : memberIds) {
            inviteMemberToRehearsal(rehearsalId, memberId);
        }

        // --- Set attendance statuses: 1 PRESENT, 1 EXCUSED, 1 NO_RESPONSE ---
        setRehearsalAttendance(rehearsalId, memberIds.get(0), "PRESENT");
        setRehearsalAttendance(rehearsalId, memberIds.get(1), "EXCUSED");
        // memberIds.get(2) stays as NO_RESPONSE

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Test attendance filter: click PRESENT (✅) ---
        WebElement presentBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#attendance-response-filter-container .response-filter-btn[data-response-filter='PRESENT']")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", presentBtn);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);

        // Verify the visible row has PRESENT status
        WebElement statusSelect = visibleRows.get(0).findElement(By.cssSelector("td select.status-select"));
        assertThat(statusSelect.getAttribute("value")).isEqualTo("PRESENT");

        // --- Click PRESENT again to deselect (should show all) ---
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", presentBtn);

        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows2 = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows2.size() == 3;
        });

        List<WebElement> allVisibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(allVisibleRows).hasSize(3);
    }

    @Test
    void attendanceFilterShouldFilterByMultipleStatuses() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "MultiAtt" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName, wait);
        createMember(firstName + "2", lastName, wait);
        createMember(firstName + "3", lastName, wait);
        createMember(firstName + "4", lastName, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        assertThat(rehearsalId).isNotNull();
        assertThat(memberIds).hasSize(4);

        // --- Invite all four members to the rehearsal ---
        for (Long memberId : memberIds) {
            inviteMemberToRehearsal(rehearsalId, memberId);
        }

        // --- Set attendance statuses: PRESENT, EXCUSED, UNEXCUSED, NO_RESPONSE ---
        setRehearsalAttendance(rehearsalId, memberIds.get(0), "PRESENT");
        setRehearsalAttendance(rehearsalId, memberIds.get(1), "EXCUSED");
        setRehearsalAttendance(rehearsalId, memberIds.get(2), "UNEXCUSED");
        // memberIds.get(3) stays as NO_RESPONSE

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Test attendance filter: click PRESENT AND EXCUSED ---
        WebElement presentBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#attendance-response-filter-container .response-filter-btn[data-response-filter='PRESENT']")));
        WebElement excusedBtn = driver.findElement(
                By.cssSelector("#attendance-response-filter-container .response-filter-btn[data-response-filter='EXCUSED']"));

        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", presentBtn);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", excusedBtn);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 2;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(2);

        // Verify statuses
        for (WebElement row : visibleRows) {
            WebElement statusSelect = row.findElement(By.cssSelector("td select.status-select"));
            String status = statusSelect.getAttribute("value");
            assertThat(status).isIn("PRESENT", "EXCUSED");
        }
    }

    @Test
    void attendanceFilterCountsShouldUpdateAfterStatusChange() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "CountUpdate" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName, wait);
        createMember(firstName + "2", lastName, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        assertThat(rehearsalId).isNotNull();
        assertThat(memberIds).hasSize(2);

        // --- Invite both members to the rehearsal ---
        for (Long memberId : memberIds) {
            inviteMemberToRehearsal(rehearsalId, memberId);
        }

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Verify initial filter counts: 2 NO_RESPONSE ---
        WebElement noResponseCount = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".filter-count-no-response")));
        assertThat(noResponseCount.getText()).isEqualTo("2");

        WebElement presentCount = driver.findElement(By.cssSelector(".filter-count-present"));
        assertThat(presentCount.getText()).isEqualTo("0");

        // --- Change first member status to PRESENT ---
        WebElement statusSelect1 = driver.findElement(By.cssSelector("select.status-select[data-member-id='" + memberIds.get(0) + "']"));
        new Select(statusSelect1).selectByValue("PRESENT");

        // Wait for HTMX reload to complete and filter counts to update
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            try {
                var countEl = driver.findElement(By.cssSelector(".filter-count-present"));
                return "1".equals(countEl.getText());
            } catch (Exception e) {
                return false;
            }
        });

        // --- Verify filter counts updated: 1 PRESENT, 1 NO_RESPONSE ---
        presentCount = driver.findElement(By.cssSelector(".filter-count-present"));
        noResponseCount = driver.findElement(By.cssSelector(".filter-count-no-response"));
        assertThat(presentCount.getText()).isEqualTo("1");
        assertThat(noResponseCount.getText()).isEqualTo("1");

        // --- Change second member status to PRESENT ---
        WebElement statusSelect2 = driver.findElement(By.cssSelector("select.status-select[data-member-id='" + memberIds.get(1) + "']"));
        new Select(statusSelect2).selectByValue("PRESENT");

        // Wait for HTMX reload to complete and filter counts to update
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            try {
                var countEl = driver.findElement(By.cssSelector(".filter-count-present"));
                return "2".equals(countEl.getText());
            } catch (Exception e) {
                return false;
            }
        });

        // --- Verify filter counts updated: 2 PRESENT, 0 NO_RESPONSE ---
        presentCount = driver.findElement(By.cssSelector(".filter-count-present"));
        noResponseCount = driver.findElement(By.cssSelector(".filter-count-no-response"));
        assertThat(presentCount.getText()).isEqualTo("2");
        assertThat(noResponseCount.getText()).isEqualTo("0");
    }

    @Test
    void combinedTextAndAttendanceFilterShouldWork() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Combined" + uid;
        String lastName = "Filter" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName + "A", wait);
        createMember(firstName + "2", lastName + "B", wait);
        createMember("Other" + uid, "Person" + uid, wait);

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

        // Wait for rehearsal to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM rehearsals WHERE date = ?", Long.class, today) > 0);

        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, today);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        Long otherMemberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, "Other" + uid);

        assertThat(rehearsalId).isNotNull();
        assertThat(memberIds).hasSize(2);
        assertThat(otherMemberId).isNotNull();

        // --- Invite all members to the rehearsal ---
        for (Long memberId : memberIds) {
            inviteMemberToRehearsal(rehearsalId, memberId);
        }
        inviteMemberToRehearsal(rehearsalId, otherMemberId);

        // --- Set attendance statuses ---
        setRehearsalAttendance(rehearsalId, memberIds.get(0), "PRESENT");
        setRehearsalAttendance(rehearsalId, memberIds.get(1), "EXCUSED");
        setRehearsalAttendance(rehearsalId, otherMemberId, "PRESENT");

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for attendance table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table[role='grid']")));

        // --- Apply text filter for first name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("attendance-filter")));
        filterInput.clear();
        filterInput.sendKeys(firstName);

        // --- Apply attendance filter for PRESENT ---
        WebElement presentBtn = driver.findElement(
                By.cssSelector("#attendance-response-filter-container .response-filter-btn[data-response-filter='PRESENT']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", presentBtn);

        // Wait for both filters to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);

        // Verify it's the member with matching first name AND PRESENT status
        WebElement nameCell = visibleRows.get(0).findElement(By.cssSelector("td:first-child"));
        WebElement statusSelect = visibleRows.get(0).findElement(By.cssSelector("td select.status-select"));
        assertThat(nameCell.getText()).contains(firstName + "1");
        assertThat(statusSelect.getAttribute("value")).isEqualTo("PRESENT");
    }

    /**
     * Regression: the attendance text filter must survive the in-page HTMX
     * reload that happens after changing a member's status (Lista obecności).
     * Before the fix, the swap replaced #rehearsals-content (the filter input
     * and all rows), losing the listeners and the filter state, so re-typing a
     * name had no effect.
     */
    @Test
    void textFilterShouldSurviveAttendanceChangeReload() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstNameA = "Alpha" + uid;
        String firstNameB = "Beta" + uid;
        String lastName = "Test" + uid;

        createMember(firstNameA, lastName, wait);
        createMember(firstNameB, lastName, wait);

        // --- Create a future-dated rehearsal via API (deterministic id) ---
        LocalDate date = LocalDate.now().plusDays(7);
        Long rehearsalId = createRehearsalViaApi(uid, date);
        assertNotNull(rehearsalId);

        Long memberIdA = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, firstNameA);
        Long memberIdB = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, firstNameB);

        inviteMemberToRehearsal(rehearsalId, memberIdA);
        inviteMemberToRehearsal(rehearsalId, memberIdB);

        // Give A a PRESENT status via API (setup), B stays NO_RESPONSE.
        setRehearsalAttendance(rehearsalId, memberIdA, "PRESENT");

        // --- Navigate to rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("table[role='grid'] tbody tr")));

        // --- Apply text filter (matches Alpha only) ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("attendance-filter")));
        filterInput.clear();
        filterInput.sendKeys(firstNameA);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                countVisibleAttendanceRows() == 1);
        assertThat(visibleAttendanceRowName()).contains(firstNameA);

        // --- Change B's status via the UI dropdown (the user action) ---
        setStatusViaUi(memberIdB, "PRESENT", wait);

        // --- Type a new filter term (matches Beta only). Expected: filter still
        //     works after the reload and shows exactly Beta. Re-look-up AFTER settle.
        WebElement freshInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("attendance-filter")));
        freshInput.clear();
        freshInput.sendKeys(firstNameB);

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                countVisibleAttendanceRows() == 1);
        assertThat(visibleAttendanceRowName()).contains(firstNameB);
        assertThat(visibleAttendanceRowName()).doesNotContain(firstNameA);
    }

    private int countVisibleAttendanceRows() {
        return driver.findElements(By.cssSelector(
                "table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"))
                .size();
    }

    private String visibleAttendanceRowName() {
        List<WebElement> rows = driver.findElements(By.cssSelector(
                "table[role='grid'] tbody tr[style=''], table[role='grid'] tbody tr:not([style*='display: none'])"));
        assertThat(rows).isNotEmpty();
        return rows.get(0).findElement(By.cssSelector("td:first-child")).getText();
    }

    /** Change a member's attendance status via the page dropdown (fires the real flow). */
    private void setStatusViaUi(Long memberId, String status, WebDriverWait wait) {
        WebElement select = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("table[role='grid'] select.status-select[data-member-id='" + memberId + "']")));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];" +
                "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));",
                select, status);

        // Wait for the HTMX swap to fully settle (see EventDetailFilterUiTest).
        final String snap = "(function(){var t=document.querySelector('table[role=\"grid\"]');"
                + "if(!t)return 'no-table';"
                + "var rows=t.querySelectorAll('tbody tr').length;"
                + "var inp=document.getElementById('attendance-filter');"
                + "return rows+':'+(inp?inp.value.length:'-');})()";
        long deadline = System.currentTimeMillis() + 10_000L;
        String last = null;
        boolean settled = false;
        while (!settled && System.currentTimeMillis() < deadline) {
            String cur = String.valueOf(((JavascriptExecutor) driver).executeScript(snap));
            if (cur.equals(last)) {
                settled = true;
            } else {
                last = cur;
            }
            Thread.yield();
        }
        assertThat(settled).as("HTMX swap did not settle within timeout").isTrue();

        // Additional wait: ensure the filter input is present and interactable after swap
        wait.until(ExpectedConditions.elementToBeClickable(By.id("attendance-filter")));

        // Extra stabilization: wait a bit more for the DOM to fully settle after the element is clickable
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    /**
     * Verification (t_ebd04e70): the rehearsal attendance filter matches a
     * participant's TAG (primary instrument) accent-insensitively, WHILE the
     * name-based predicate keeps working unchanged.  A and B are created; A is
     * tagged "Trąbka" (Polish diacritic), B has no tag.
     *   - Typing nonsense by name hides both (no accidental tag match).
     *   - Typing the EXACT "Trąbka" surface ONLY A  (tag branch, diacritic form).
     *   - Typing the ASCII fold "trabka" also surfaces ONLY A  (accent-aware).
     *   - Typing B's FIRST NAME still surfaces exactly B (name branch unchanged).
     */
    @Test
    void tagFilterShouldMatchPrimaryInstrumentAccentAware() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String lastName = "Test" + uid;

        // Create A tagged "Trąbka", B untagged.
        createAndTagMember("TrumpetGuy" + uid, lastName, "Trąbka", wait);
        createMember("NoTag" + uid, lastName, wait);

        LocalDate date = LocalDate.now().plusDays(7);
        Long rehearsalId = createRehearsalViaApi(uid, date);
        assertNotNull(rehearsalId);

        Long taggedId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, "TrumpetGuy" + uid);
        Long untaggedId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, "NoTag" + uid);

        inviteMemberToRehearsal(rehearsalId, taggedId);
        inviteMemberToRehearsal(rehearsalId, untaggedId);

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("table[role='grid'] tbody tr")));

        // --- 1) Nonsense by name — both rows hidden. ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("attendance-filter")));
        filterInput.clear();
        filterInput.sendKeys("zzznomatch-" + uid);
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleAttendanceRows() == 0);
        assertThat(countVisibleAttendanceRows()).isZero();

        // --- 2) EXACT "Trąbka" — ONLY the Trąbka row via tag branch. ---
        filterInput.clear();
        filterInput.sendKeys("Trąbka");
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleAttendanceRows() == 1);
        assertThat(countVisibleAttendanceRows()).isEqualTo(1);
        assertThat(visibleAttendanceRowName()).contains("TrumpetGuy" + uid);

        // --- 3) ASCII fold "trabka" — still ONLY the Trąbka row. ---
        filterInput.clear();
        filterInput.sendKeys("trabka");
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleAttendanceRows() == 1);
        assertThat(countVisibleAttendanceRows()).isEqualTo(1);
        assertThat(visibleAttendanceRowName()).contains("TrumpetGuy" + uid);

        // --- 4) Name-based predicate is UNCHANGED: B's first name surfaces B. ---
        filterInput.clear();
        filterInput.sendKeys("NoTag" + uid);
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleAttendanceRows() == 1);
        assertThat(countVisibleAttendanceRows()).isEqualTo(1);
        assertThat(visibleAttendanceRowName()).contains("NoTag" + uid);

        System.out.println("[rehearsal-tag-filter] Trąbka/trabka surface ONLY the tagged row via instrument; name branch OK");
    }

    /**
     * Create a member and attach "instrument" as their PRIMARY — directly in the
     * join table, which is what the rehearsal detail template reads as
     * ${m.primaryInstrument}.  Mirrors the event-side createMemberWithInstrument().
     */
    private void createAndTagMember(String firstName, String lastName,
                                    String instrument, WebDriverWait wait) throws Exception {
        createMember(firstName, lastName, wait);
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM instruments WHERE name = ?", Integer.class, instrument);
            if (count == null || count == 0) {
                jdbcTemplate.update("INSERT INTO instruments (name) VALUES (?)", instrument);
            }
        } catch (Exception ignored) { /* duplicate row or race on name lookup — instrument is created once */ }
        Long memberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, firstName);
        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE name = ?", Long.class, instrument);
        jdbcTemplate.update(
                "MERGE INTO member_instruments (member_id, instrument_id, is_primary) KEY(member_id, instrument_id) " +
                "VALUES (?, ?, TRUE)",
                memberId, instrumentId);
    }
}