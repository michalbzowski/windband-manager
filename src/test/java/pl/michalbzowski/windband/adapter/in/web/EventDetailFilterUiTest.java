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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for filtering participants on the event detail page.
 * Tests text filtering (first name, last name, instrument/tag) and response status filtering.
 */
class EventDetailFilterUiTest extends UiTestBase {

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

        // --- Create an event via UI ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Filter Test Event " + uid, today);
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for event to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Filter Test Event " + uid) > 0);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class,
                "Filter Test Event " + uid);

        Long memberId1 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, firstName);

        Long memberId2 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "OtherFirst" + uid);

        assertThat(eventId).isNotNull();
        assertThat(memberId1).isNotNull();
        assertThat(memberId2).isNotNull();

        // --- Invite both members to the event ---
        inviteMemberToEvent(eventId, memberId1);
        inviteMemberToEvent(eventId, memberId2);

        // --- Navigate to event detail ---
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Wait for participants table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // --- Test text filter by first name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participant-filter")));
        filterInput.clear();
        filterInput.sendKeys(firstName);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
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

        // --- Create an event via UI ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Filter Test Event " + uid, today);
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for event to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Filter Test Event " + uid) > 0);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class,
                "Filter Test Event " + uid);

        Long memberId1 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE last_name = ?", Long.class, lastName);

        Long memberId2 = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE last_name = ?", Long.class, "OtherLast" + uid);

        assertThat(eventId).isNotNull();
        assertThat(memberId1).isNotNull();
        assertThat(memberId2).isNotNull();

        // --- Invite both members to the event ---
        inviteMemberToEvent(eventId, memberId1);
        inviteMemberToEvent(eventId, memberId2);

        // --- Navigate to event detail ---
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Wait for participants table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // --- Test text filter by last name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participant-filter")));
        filterInput.clear();
        filterInput.sendKeys(lastName);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);
        assertThat(visibleRows.get(0).findElement(By.cssSelector("td:first-child")).getText())
                .contains(lastName);
    }

    @Test
    void responseFilterShouldFilterByConfirmed() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "RespFilter" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName, wait);
        createMember(firstName + "2", lastName, wait);
        createMember(firstName + "3", lastName, wait);

        // --- Create an event via UI ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Response Filter Test " + uid, today);
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for event to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Response Filter Test " + uid) > 0);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class,
                "Response Filter Test " + uid);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        assertThat(eventId).isNotNull();
        assertThat(memberIds).hasSize(3);

        // --- Invite all three members to the event ---
                        for (Long memberId : memberIds) {
                            inviteMemberToEvent(eventId, memberId);
                        }

                // Wait for participations to be created
                Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
                    Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM event_participations WHERE event_id = ?", Long.class, eventId);
                    return count >= 3;
                });
                System.out.println("All members invited, participations created");

                // --- Set responses via XHR API (so they're committed and visible to web request) ---
                setEventResponse(eventId, memberIds.get(0), "CONFIRMED");
                setEventResponse(eventId, memberIds.get(1), "DECLINED");
                // memberIds.get(2) stays as NO_RESPONSE
                System.out.println("Responses set via API");

        // --- Navigate to event detail ---
        driver.get(baseUrl() + "/events/" + eventId);
        System.out.println("Navigated to event detail page for response filter test, current URL: " + driver.getCurrentUrl());
        System.out.println("Page title: " + driver.getTitle());
        System.out.println("Page source length: " + driver.getPageSource().length());
        System.out.println("Page source preview: " + driver.getPageSource().substring(0, Math.min(2000, driver.getPageSource().length())));

        // Check if we got an error page
        String pageSource = driver.getPageSource();
        if (pageSource.contains("xml-viewer-style") || pageSource.contains("404") || pageSource.contains("Error") || pageSource.contains("Whitelabel")) {
            System.out.println("ERROR PAGE DETECTED!");
            System.out.println("FULL ERROR PAGE SOURCE: " + pageSource);

            if (pageSource.contains("Whitelabel Error Page")) {
                System.out.println("WHITELABEL ERROR PAGE DETECTED");
            }
            if (pageSource.contains("EventNotFound")) {
                System.out.println("EVENT NOT FOUND ERROR DETECTED");
            }
            if (pageSource.contains("stackTrace") || pageSource.contains("Exception") || pageSource.contains("exception")) {
                System.out.println("EXCEPTION DETECTED IN PAGE SOURCE");
            }
            if (pageSource.contains("null key")) {
                System.out.println("NULL KEY ERROR DETECTED");
            }
            if (pageSource.contains("Jackson")) {
                System.out.println("JACKSON ERROR DETECTED");
            }
            if (pageSource.contains("Map") && pageSource.contains("timestamp")) {
                System.out.println("SPRING ERROR RESPONSE DETECTED (XML/JSON)");
            }
        }

        System.out.println("About to wait for events-content element...");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Wait for participants table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));
        System.out.println("Participants table loaded for response filter test");

        // --- Test response filter: click CONFIRMED (✅) ---
        WebElement confirmedBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".response-filter-btn[data-response-filter='CONFIRMED']")));
        System.out.println("Found CONFIRMED button, clicking...");
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", confirmedBtn);
        System.out.println("Clicked CONFIRMED button");

        // Debug: Check the active filters input and response values
        String activeFiltersValue = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('active-response-filters').value;");
        System.out.println("Active filters value after click: " + activeFiltersValue);

        List<WebElement> allRows = driver.findElements(By.cssSelector("#participants-table tbody tr"));
        System.out.println("Total rows in table: " + allRows.size());
        for (int i = 0; i < allRows.size(); i++) {
            WebElement row = allRows.get(i);
            String responseValue = "";
            try {
                WebElement select = row.findElement(By.cssSelector("td[data-label='Odpowiedź'] select"));
                responseValue = select.getAttribute("value");
            } catch (Exception e) {
                responseValue = "NO_SELECT";
            }
            String display = row.getAttribute("style");
            System.out.println("Row " + i + ": response=" + responseValue + ", style=" + display);
        }

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            System.out.println("Visible rows after filter: " + visibleRows.size());
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);

        // Verify the visible row has CONFIRMED response
        WebElement responseSelect = visibleRows.get(0).findElement(By.cssSelector("td[data-label='Odpowiedź'] select"));
        assertThat(responseSelect.getAttribute("value")).isEqualTo("CONFIRMED");

        // --- Click CONFIRMED again to deselect (should show all) ---
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", confirmedBtn);

        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows2 = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            return visibleRows2.size() == 3;
        });

        List<WebElement> allVisibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(allVisibleRows).hasSize(3);
    }

    @Test
    void responseFilterShouldFilterByMultipleStatuses() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "MultiResp" + uid;
        String lastName = "Test" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName, wait);
        createMember(firstName + "2", lastName, wait);
        createMember(firstName + "3", lastName, wait);
        createMember(firstName + "4", lastName, wait);

        // --- Create an event via UI ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Multi Response Filter Test " + uid, today);
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for event to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Multi Response Filter Test " + uid) > 0);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class,
                "Multi Response Filter Test " + uid);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        assertThat(eventId).isNotNull();
        assertThat(memberIds).hasSize(4);

        // --- Invite all four members to the event ---
        for (Long memberId : memberIds) {
            inviteMemberToEvent(eventId, memberId);
        }

        // --- Set responses: CONFIRMED, DECLINED, LATER, NO_RESPONSE ---
        setEventResponse(eventId, memberIds.get(0), "CONFIRMED");
        setEventResponse(eventId, memberIds.get(1), "DECLINED");
        setEventResponse(eventId, memberIds.get(2), "LATER");
        // memberIds.get(3) stays as NO_RESPONSE

        // --- Navigate to event detail ---
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Wait for participants table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // --- Test response filter: click CONFIRMED AND DECLINED ---
        WebElement confirmedBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".response-filter-btn[data-response-filter='CONFIRMED']")));
        WebElement declinedBtn = driver.findElement(
                By.cssSelector(".response-filter-btn[data-response-filter='DECLINED']"));

        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", confirmedBtn);
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", declinedBtn);

        // Wait for filter to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 2;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(2);

        // Verify responses
        for (WebElement row : visibleRows) {
            WebElement responseSelect = row.findElement(By.cssSelector("td[data-label='Odpowiedź'] select"));
            String response = responseSelect.getAttribute("value");
            assertThat(response).isIn("CONFIRMED", "DECLINED");
        }
    }

    @Test
    void combinedTextAndResponseFilterShouldWork() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Combined" + uid;
        String lastName = "Filter" + uid;

        // --- Create members via UI ---
        createMember(firstName + "1", lastName + "A", wait);
        createMember(firstName + "2", lastName + "B", wait);
        createMember("Other" + uid, "Person" + uid, wait);

        // --- Create an event via UI ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));

        String today = LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Combined Filter Test " + uid, today);
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for event to be persisted
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Combined Filter Test " + uid) > 0);

        Long eventId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM band_events WHERE name = ?", Long.class,
                "Combined Filter Test " + uid);

        List<Long> memberIds = jdbcTemplate.query(
                "SELECT id FROM members WHERE first_name LIKE ? ORDER BY id",
                (rs, rowNum) -> rs.getLong("id"),
                firstName + "%");

        Long otherMemberId = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, "Other" + uid);

        assertThat(eventId).isNotNull();
        assertThat(memberIds).hasSize(2);
        assertThat(otherMemberId).isNotNull();

        // --- Invite all members to the event ---
        for (Long memberId : memberIds) {
            inviteMemberToEvent(eventId, memberId);
        }
        inviteMemberToEvent(eventId, otherMemberId);

        // --- Set responses ---
        setEventResponse(eventId, memberIds.get(0), "CONFIRMED");
        setEventResponse(eventId, memberIds.get(1), "DECLINED");
        setEventResponse(eventId, otherMemberId, "CONFIRMED");

        // --- Navigate to event detail ---
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Wait for participants table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // --- Apply text filter for first name ---
        WebElement filterInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participant-filter")));
        filterInput.clear();
        filterInput.sendKeys(firstName);

        // --- Apply response filter for CONFIRMED ---
        WebElement confirmedBtn = driver.findElement(
                By.cssSelector(".response-filter-btn[data-response-filter='CONFIRMED']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", confirmedBtn);

        // Wait for both filters to apply
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> {
            List<WebElement> visibleRows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
            return visibleRows.size() == 1;
        });

        List<WebElement> visibleRows = driver.findElements(
                By.cssSelector("#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(visibleRows).hasSize(1);

        // Verify it's the member with matching first name AND CONFIRMED response
        WebElement nameCell = visibleRows.get(0).findElement(By.cssSelector("td:first-child"));
        WebElement responseSelect = visibleRows.get(0).findElement(By.cssSelector("td[data-label='Odpowiedź'] select"));
        assertThat(nameCell.getText()).contains(firstName + "1");
        assertThat(responseSelect.getAttribute("value")).isEqualTo("CONFIRMED");
    }

    /**
     * Regression test (t_cc648529 / t_79070964): a typed participant filter and an
     * active response-status filter must SURVIVE the HTMX outerHTML swap of
     * #events-content that is triggered when a member's response is changed.
     * Without persistence, saving a response re-renders the input with value="" and
     * resets the status buttons, silently discarding the user's active filter.
     */
    @Test
    void textAndResponseFilterShouldSurviveResponseChangeReload() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstNameA = "Keep" + uid;
        String firstNameB = "Other" + uid;
        String lastName = "Test" + uid;

        createMember(firstNameA, lastName, wait);
        createMember(firstNameB, lastName, wait);

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala koncertowa';",
                "Persist Filter Test " + uid, LocalDate.now().toString());
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Persist Filter Test " + uid) > 0);
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, "Persist Filter Test " + uid);
        Long memberIdA = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, firstNameA);
        Long memberIdB = jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, firstNameB);

        inviteMemberToEvent(eventId, memberIdA);
        inviteMemberToEvent(eventId, memberIdB);
        setEventResponse(eventId, memberIdA, "CONFIRMED");
        setEventResponse(eventId, memberIdB, "LATER");

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        WebElement filterInput = driver.findElement(By.id("participant-filter"));
        filterInput.clear();
        filterInput.sendKeys(firstNameA);
        List<WebElement> statusBtns = driver.findElements(
                By.cssSelector(".response-filter-btn[data-response-filter='CONFIRMED']"));
        assertThat(statusBtns).isNotEmpty();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", statusBtns.get(0));

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                countVisibleRows() == 1);
        assertThat(visibleRowFirstCellText()).contains(firstNameA);

        WebElement responseSelect = driver.findElement(
                By.cssSelector("#events-content .response-select[data-member-id=\"" + memberIdB + "\"]"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value='CONFIRMED';" +
                "arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", responseSelect);

        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                "CONFIRMED".equals(jdbcTemplate.queryForObject(
                        "SELECT response FROM event_participations WHERE event_id = ? AND member_id = ?",
                        String.class, eventId, memberIdB))
                && countVisibleRows() == 1);

        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains(firstNameA);
        assertThat(visibleRowFirstCellText()).doesNotContain(firstNameB);

        String inputValue = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('participant-filter').value;");
        assertThat(inputValue).isEqualTo(firstNameA);
        String activeFilters = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('active-response-filters').value;");
        assertThat(activeFilters).contains("CONFIRMED");
    }

    /**
     * Verification (t_bd84945b): the first-name filter must survive TWO SUCCESSIVE
     * response saves in a row, re-filtering correctly after each. The manual
     * checklist requires "change response for multiple members in succession;
     * filter must survive each save." Each UI select change posts to the API and
     * triggers an HTMX outerHTML swap of #events-content — a fresh DOM copy that
     * previously destroyed listeners + state.
     */
    @Test
    void textFilterShouldSurviveTwoSuccessiveResponseSaves() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstNameA = "Survive2" + uid;
        String firstNameB = "GhostA" + uid;
        String firstNameC = "GhostB" + uid;
        String lastName = "Test" + uid;

        createMember(firstNameA, lastName, wait);
        createMember(firstNameB, lastName, wait);
        createMember(firstNameC, lastName, wait);

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala';",
                "Successive Saves Test " + uid, LocalDate.now().toString());
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Successive Saves Test " + uid) > 0);
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, "Successive Saves Test " + uid);
        Long memberIdA = lookupFirst("first_name", firstNameA);
        Long memberIdB = lookupFirst("first_name", firstNameB);
        Long memberIdC = lookupFirst("first_name", firstNameC);

        inviteMemberToEvent(eventId, memberIdA);
        inviteMemberToEvent(eventId, memberIdB);
        inviteMemberToEvent(eventId, memberIdC);
        // Setup: A CONFIRMED, B LATER, C NO_RESPONSE.
        setEventResponse(eventId, memberIdA, "CONFIRMED");
        setEventResponse(eventId, memberIdB, "LATER");

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // --- Apply first-name filter: only A matches. ---
        WebElement filterInput = driver.findElement(By.id("participant-filter"));
        filterInput.clear();
        filterInput.sendKeys(firstNameA);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> countVisibleRows() == 1);

        // --- SAVE 1: change B from LATER -> CONFIRMED via the UI dropdown. ---
        saveResponseViaUi(eventId, memberIdB, "CONFIRMED");
        // After reload, DB committed and the filter still isolates A.
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                "CONFIRMED".equals(dbResponse(eventId, memberIdB)) && countVisibleRows() == 1);
        assertThat(visibleRowFirstCellText()).contains(firstNameA);

        // --- SAVE 2: change C from NO_RESPONSE -> DECLINED via the UI dropdown. ---
        saveResponseViaUi(eventId, memberIdC, "DECLINED");
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                "DECLINED".equals(dbResponse(eventId, memberIdC)) && countVisibleRows() == 1);

        // Filter must STILL be alive and applied after both saves.
        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains(firstNameA);
        String inputValue = filterValue();
        assertThat(inputValue).isEqualTo(firstNameA);
    }

    /**
     * Verification (t_bd84945b): a LAST-NAME text filter must survive a response
     * change reload (the exact 'Będę' flow from the manual checklist, but by
     * surname). Confirms persistence is not tied to first names only.
     */
    @Test
    void lastNameFilterShouldSurviveResponseSave() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String lastNameA = "KeepLast" + uid;
        String lastNameB = "OtherLast" + uid;

        createMember("Alpha" + uid, lastNameA, wait);
        createMember("Beta" + uid, lastNameB, wait);

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala';",
                "Last Name Persist Test " + uid, LocalDate.now().toString());
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Last Name Persist Test " + uid) > 0);
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, "Last Name Persist Test " + uid);
        Long memberIdA = lookupFirst("last_name", lastNameA);
        Long memberIdB = lookupFirst("last_name", lastNameB);

        inviteMemberToEvent(eventId, memberIdA);
        inviteMemberToEvent(eventId, memberIdB);
        setEventResponse(eventId, memberIdB, "LATER");

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        WebElement filterInput = driver.findElement(By.id("participant-filter"));
        filterInput.clear();
        filterInput.sendKeys(lastNameA);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> countVisibleRows() == 1);
        assertThat(visibleRowFirstCellText()).contains(lastNameA);

        // Save B's response (LATER -> CONFIRMED) via UI; the reload must not reset the surname filter.
        saveResponseViaUi(eventId, memberIdB, "CONFIRMED");
        Awaitility.await().atMost(Duration.ofSeconds(15)).until(() ->
                "CONFIRMED".equals(dbResponse(eventId, memberIdB)) && countVisibleRows() == 1);

        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains(lastNameA);
        assertThat(filterValue()).isEqualTo(lastNameA);
    }

    /**
     * Verification (t_ebd04e70): the participant filter also matches the member's
     * TAG — their instrument — and does so accent-insensitively.  Two members are
     * created: one tagged "Trąbka" (Polish diacritic) and one untagged.  Typing
     * either the exact diacritic form or the ASCII fold ("trabka") surfaces EXACTLY
     * the one row that carries the Trąbka tag; typing nonsense by name hides both.
     */
    @Test
    void tagFilterShouldMatchInstrumentTagAccentAware() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String lastName = "Test" + uid;

        // Member A tagged "Trąbka" (Polish diacritic — exercises the accent-aware fold).
        createMemberWithInstrument("TrumpetGuy" + uid, lastName, "Trąbka", wait);
        // Member B untagged — a name-only match must never happen via this tag.
        createMember("NoTag" + uid, lastName, wait);

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala';",
                "Tag Acc Test " + uid, LocalDate.now().toString());
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Tag Acc Test " + uid) > 0);
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, "Tag Acc Test " + uid);
        Long taggedId = lookupFirst("first_name", "TrumpetGuy" + uid);
        Long untaggedId = lookupFirst("first_name", "NoTag" + uid);

        inviteMemberToEvent(eventId, taggedId);
        inviteMemberToEvent(eventId, untaggedId);

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        int total = driver.findElements(By.cssSelector("#participants-table tbody tr")).size();
        assertThat(total).isEqualTo(2);

        // 1) Nonsense name — neither row should match by tag or by name.
        WebElement filterInput = driver.findElement(By.id("participant-filter"));
        filterInput.clear();
        filterInput.sendKeys("zzznomatch-" + uid);
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleRows() == 0);
        assertThat(countVisibleRows()).isZero();

        // 2) ExACT diacritic form — matches ONLY the Trąbka row via the tag branch.
        filterInput.clear();
        filterInput.sendKeys("Trąbka");
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleRows() == 1);
        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains("TrumpetGuy" + uid);

        // 3) ASCII folded form — user types "trabka" (no diacritic), still matches.
        filterInput.clear();
        filterInput.sendKeys("trabka");
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleRows() == 1);
        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains("TrumpetGuy" + uid);

        // 4) Name-based predicate is unchanged: typing the untagged member's
        //    first name surfaces exactly that row.
        filterInput.clear();
        filterInput.sendKeys("NoTag" + uid);
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> countVisibleRows() == 1);
        assertThat(countVisibleRows()).isEqualTo(1);
        assertThat(visibleRowFirstCellText()).contains("NoTag" + uid);

        System.out.println("[tag-filter] Trąbka/trabka surface ONLY the tagged row; name branch unchanged");
    }

    /**
     * Verification (t_bd84945b) — navigate AWAY from event detail and BACK.
     *
     * NOTE on the manual checklist's "filter should reset (expected full-page
     * behaviour)" expectation: the implemented fix DELIBERATELY persists the filter
     * in sessionStorage, so after a same-tab navigation-away-then-back the typed
     * term is RESTORED and still applied (this was observed live: input kept its
     * value, visibleRows==1). A genuinely fresh session / new tab starts unfiltered.
     * This test therefore asserts the only robust invariant — that whatever state we
     * arrive in on reload, the view is self-consistent — and PRINTS which way it went,
     * so the acceptance report can state the real behaviour to the user rather than
     * silently assuming "reset".
     */
    @Test
    void filterStateAfterNavigationAwayAndBackIsConsistent() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstNameA = "ResetKeep" + uid;
        String firstNameB = "ResetOther" + uid;
        String lastName = "Test" + uid;

        createMember(firstNameA, lastName, wait);
        createMember(firstNameB, lastName, wait);

        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala';",
                "Reset Nav Test " + uid, LocalDate.now().toString());
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/events"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM band_events WHERE name = ?", Long.class,
                        "Reset Nav Test " + uid) > 0);
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, "Reset Nav Test " + uid);
        Long memberIdA = lookupFirst("first_name", firstNameA);
        Long memberIdB = lookupFirst("first_name", firstNameB);

        inviteMemberToEvent(eventId, memberIdA);
        inviteMemberToEvent(eventId, memberIdB);

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        WebElement filterInput = driver.findElement(By.id("participant-filter"));
        filterInput.clear();
        filterInput.sendKeys(firstNameA);
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> countVisibleRows() == 1);
        assertThat(countVisibleRows()).isEqualTo(1);

        // Navigate AWAY (events list) and then BACK to the same event detail via the list link.
        driver.get(baseUrl() + "/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#events-list-container")));
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        // Observe the real post-navigation state. The fix persists the filter in
        // sessionStorage, so a same-tab away+back RESTORES the term (still applied).
        String inputValueAfterNav = filterValue();
        int visibleAfterNav = countVisibleRows();
        System.out.println("[reset-nav] after away+back -> input='"+inputValueAfterNav+"', visibleRows="+visibleAfterNav);

        // The only robust invariant: the reloaded view is self-consistent.
        if (inputValueAfterNav.trim().isEmpty()) {
            // Fresh state: no filter, all 2 rows shown.
            assertThat(visibleAfterNav).isEqualTo(2);
        } else {
            // Restored state: term survives and correctly isolates exactly A.
            assertThat(visibleAfterNav).isEqualTo(1);
            assertThat(inputValueAfterNav).contains(firstNameA);
            assertThat(visibleRowFirstCellText()).contains(firstNameA);
        }
    }

    private String filterValue() {
        return (String) ((JavascriptExecutor) driver).executeScript(
                "var el = document.getElementById('participant-filter'); return el ? el.value : '';");
    }

    private Long lookupFirst(String column, String value) {
        if ("last_name".equals(column)) {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM members WHERE last_name = ?", Long.class, value);
        }
        return jdbcTemplate.queryForObject(
                "SELECT id FROM members WHERE first_name = ?", Long.class, value);
    }

    private String dbResponse(Long eventId, Long memberId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_participations WHERE event_id = ? AND member_id = ?",
                Integer.class, eventId, memberId);
        if (n == null || n == 0) return null;
        Object r = jdbcTemplate.queryForObject(
                "SELECT response FROM event_participations WHERE event_id = ? AND member_id = ?",
                String.class, eventId, memberId);
        return r == null ? "NO_RESPONSE" : String.valueOf(r);
    }

    private void saveResponseViaUi(Long eventId, Long memberId, String newResponse) {
        // Change the member's response through the ACTUAL UI <select> so the real HTMX
        // outerHTML swap fires (the exact action that used to destroy listeners + state).
        WebElement select = driver.findElement(By.cssSelector(
                "#events-content .response-select[data-member-id=\"" + memberId + "\"]"));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = arguments[1];" +
                "arguments[0].dispatchEvent(new Event('change', {bubbles:true}));",
                select, newResponse);
    }

    private int countVisibleRows() {
        return driver.findElements(By.cssSelector(
                "#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"))
                .size();
    }

    private String visibleRowFirstCellText() {
        List<WebElement> rows = driver.findElements(By.cssSelector(
                "#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(rows).isNotEmpty();
        return rows.get(0).findElement(By.cssSelector("td:first-child")).getText();
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
     * Create a member and assign them a specific instrument (their "tag") — the row's
     * INSTRUMENT is what the tag-filter match relies on.  Direct-DB path mirrors the
     * pattern inviteMemberToEvent() already uses for participation rows: fast, stable,
     * no extra browser round-trips.
     */
    private void createMemberWithInstrument(String firstName, String lastName,
                                            String instrument, WebDriverWait wait) throws Exception {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM members WHERE first_name = ?", Long.class, firstName) > 0);

        // Ensure the requested instrument exists in the band.
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM instruments WHERE name = ?", Integer.class, instrument);
            if (count == null || count == 0) {
                jdbcTemplate.update("INSERT INTO instruments (name) VALUES (?)", instrument);
            }
        } catch (Exception ignored) { /* a unique-constraint hit is fine */ }

        // Link the member to the instrument as primary (what p.instrumentName in the
        // detail row reads — see ParticipationDto / EventQueryService).  H2 supports
        // MERGE INTO; we use it because a second createMemberWithInstrument for the
        // same member would otherwise trip UNIQUE(member_id, instrument_id).
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
