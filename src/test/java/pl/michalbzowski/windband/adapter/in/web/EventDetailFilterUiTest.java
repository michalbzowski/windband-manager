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
}