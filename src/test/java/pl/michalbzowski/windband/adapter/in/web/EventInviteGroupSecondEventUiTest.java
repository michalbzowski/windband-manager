package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

import pl.michalbzowski.windband.UiTestBase;

/**
 * Reproduces the reported bug: a group can be invited to one event, but inviting
 * the SAME group to a SECOND event does nothing. Root cause was that the event
 * detail page handlers were bound only on full page load (DOMContentLoaded), while
 * navigation from the list to an event detail happens via HTMX (which does not
 * re-execute the page-level <script>), so #invite-group-btn had no handler on the
 * second event. This test navigates via HTMX ("Szczegóły") to prove handlers are
 * re-bound after every swap.
 */
class EventInviteGroupSecondEventUiTest extends UiTestBase {

    @Test
    void inviteSameGroupToTwoEventsViaHtmxNavigation() throws Exception {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAsAdmin(driver, wait);

        String uid = "g2" + System.nanoTime();
        createMemberViaUi(driver, wait, "Alpha" + uid, "Kowalski" + uid);
        createMemberViaUi(driver, wait, "Beta" + uid, "Nowak" + uid);
        Long alphaId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "Alpha" + uid);
        Long betaId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM members WHERE first_name = ?", Long.class, "Beta" + uid);
        System.out.println("[TEST] alphaId=" + alphaId + " betaId=" + betaId);

        // Band of the members we just created (the current team's band) — events
        // must belong to this band to appear in the list (which is band-scoped).
        Long bandId = jdbcTemplate.queryForObject(
                "SELECT band_id FROM members WHERE id = ?", Long.class, alphaId);
        System.out.println("[TEST] bandId=" + bandId);
        assertThat(bandId).isNotNull();

        String groupName = "GrupaTest" + uid;

        // Manual group with both members
        // Selenium: synchroniczny XHR zwraca ID dopiero po zakończeniu zapisu grupy.
        String groupIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open('POST', '/api/groups', false);"
                        + "xhr.setRequestHeader('Content-Type', 'application/json');"
                        + "xhr.send(JSON.stringify({name: 'GrupaTest" + uid + "', description: 'test'}));"
                        + "return JSON.parse(xhr.responseText).id.toString();");
        Long groupId = groupIdStr != null ? Long.valueOf(groupIdStr) : null;
        System.out.println("[TEST] groupId=" + groupId);
        assertThat(groupId).isNotNull();
        addMemberToGroupViaApi(driver, groupId, alphaId);
        addMemberToGroupViaApi(driver, groupId, betaId);

        // Two events
        Long eventId1 = createEventViaApi(driver, uid + "A", bandId);
        Long eventId2 = createEventViaApi(driver, uid + "B", bandId);
        System.out.println("[TEST] eventId1=" + eventId1 + " eventId2=" + eventId2);
        assertThat(eventId1).isNotNull();
        assertThat(eventId2).isNotNull();

        // ---- Navigate to EVENT 1 via HTMX (click "Szczegóły" in the list) ----
        driver.get(baseUrl() + "/events");
        System.out.println("[TEST] URL after /events = " + driver.getCurrentUrl());
        System.out.println("[TEST] page has #events-list-container = " + !driver.findElements(By.id("events-list-container")).isEmpty());
        System.out.println("[TEST] page contains 'event-4' = " + driver.getPageSource().contains("event-" + eventId1));
        System.out.println("[TEST] page contains event name = " + driver.getPageSource().contains("Wydarzenie" + uid + "A"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-" + eventId1)));
        clickSzczegoly(wait, eventId1);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));

        // Open modal, select group, invite
        jsClick(driver.findElement(By.id("open-invite-group-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-group-modal")));
        wait.until(d -> (Boolean) ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-group-modal').open === true;"));
        selectAndInviteGroupModal(wait, groupId, groupName);

        // Event 1 should now have both members. Poll the JDBC connection for the
        // persisted event_participations rows — the source of truth, independent of
        // render timing on slow CI runners.
        assertEventHasMembers(driver, wait, eventId1, alphaId, betaId);

        // ---- Navigate BACK to list (click "Powrót" — HTMX) then to EVENT 2 via HTMX.
        // This is the core regression: after two HTMX navigations the modal handler
        // must still be bound on the second event detail page.
        clickPowrot(wait);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-" + eventId2)));
        clickSzczegoly(wait, eventId2);
        // Wait for HTMX to settle after navigation so handlers are re-bound
        waitForHtmxSettle();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));

        // Invite the SAME group to the second event (via modal)
        jsClick(driver.findElement(By.id("open-invite-group-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-group-modal")));
        wait.until(d -> (Boolean) ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-group-modal').open === true;"));
        selectAndInviteGroupModal(wait, groupId, groupName);

        // Event 2 should now have both members (this is the reported bug). Same
        // JDBC poll as event 1.
        assertEventHasMembers(driver, wait, eventId2, alphaId, betaId);
    }

    private void clickSzczegoly(WebDriverWait wait, Long eventId) {
        WebElement row = driver.findElement(By.id("event-" + eventId));
        WebElement btn = row.findElement(By.xpath(".//a[contains(text(),'Szczegóły')]"));
        jsClick(btn);
    }

    private void clickPowrot(WebDriverWait wait) {
        // The "Powrót" link on event/rehearsal detail pages is now the
        // unified detail-actions-bar back icon (fragments/detail-page-actions-bar.html).
        // The bar's back <a> still carries aria-label="Powrót" and title="Powrót",
        // so we locate it via aria-label for robustness.
        WebElement back = driver.findElement(By.cssSelector(".detail-actions-bar .detail-back-link"));
        jsClick(back);
    }

    private void selectAndInviteGroupModal(WebDriverWait wait, Long groupId, String groupName) {
        // Match the EXACT group name (incl. unique uid), not a hardcoded "GrupaTest"
        // substring — otherwise the XPath grabs the first "GrupaTest*" row left in the
        // modal by a previous test (UiTestBase.cleanDatabase() does not TRUNCATE the
        // groups table, so they accumulate across the full suite) and the invite
        // POSTs the wrong groupId, adding zero participants.
        driver.findElement(By.xpath(
                "//*[@id='invite-group-modal']//label[contains(text(), '" + groupName + "')]/preceding-sibling::input[@type='checkbox']"))
                .click();
        jsClick(driver.findElement(By.id("invite-group-selected-btn")));
        // Selenium: czekamy na wiersze uczestników wyrenderowane po swapie HTMX.
        wait.until(d -> d.findElements(
                By.cssSelector("#participants-table tbody tr[data-member-id]")).size() >= 2);
        waitForHtmxSettle();
    }

    private void jsClick(WebElement el) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el);
    }

    private void assertEventHasMembers(WebDriver driver, WebDriverWait wait,
                                        Long eventId, Long alphaId, Long betaId) {
        // Poll the database for the participants. This is the source of truth —
        // much more reliable than re-rendering the event detail page and parsing
        // HTML on the slow CI runner (Ubuntu + chromium 150), where the previous
        // full-page-nav variant still timed out 15s even though the POST had
        // succeeded. The UI half of the test is already covered by the click
        // sequence above; this assertion verifies the REAL flow ("the invite
        // persists") rather than the timing of the page render.
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_participations WHERE event_id = ? AND member_id IN (?, ?)",
                    Integer.class, eventId, alphaId, betaId);
            assertThat(count)
                    .as("Both group members should be persisted as event participants after inviting the group")
                    .isEqualTo(2);
        });
    }

    private Long createEventViaApi(WebDriver driver, String name, Long bandId) {
        // Selenium: synchroniczny XHR zwraca ID dopiero po zakończeniu zapisu wydarzenia.
        String eventIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open('POST', '/api/events', false);"
                        + "xhr.setRequestHeader('Content-Type', 'application/json');"
                        + "xhr.send(JSON.stringify({name: 'Wydarzenie" + name + "', date: '" + LocalDate.now() + "',"
                        + "  startTime: '18:00', endTime: '20:00', paymentType: 'FREE', eventType: 'CONCERT', bandId: " + bandId + "}));"
                        + "return JSON.parse(xhr.responseText).id.toString();");
        return eventIdStr != null ? Long.valueOf(eventIdStr) : null;
    }

    private void createMemberViaUi(WebDriver driver, WebDriverWait wait, String first, String last) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member-form")));
        fillField("firstName", first);
        fillField("lastName", last);
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
    }

    private void addMemberToGroupViaApi(WebDriver driver, Long groupId, Long memberId) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return fetch('/api/groups/' + arguments[0] + '/members/' + arguments[1], {"
                        + "  method: 'POST'"
                        + "});", groupId, String.valueOf(memberId));
    }

    private void loginAsAdmin(WebDriver driver, WebDriverWait wait) {
        driver.get(baseUrl() + "/login");
        driver.findElement(org.openqa.selenium.By.name("username")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.name("password")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.cssSelector("button[type='submit']")).click();
        wait.until(org.openqa.selenium.support.ui.ExpectedConditions.not(
                org.openqa.selenium.support.ui.ExpectedConditions.urlContains("/login")));
    }

    private void fillField(String name, String value) {
        org.openqa.selenium.WebElement el = driver.findElement(org.openqa.selenium.By.name(name));
        el.clear();
        el.sendKeys(value);
    }

    private void waitForHtmxSettle() {
        // Selenium: po nawigacji HTMX czekamy na post-swapowy fragment szczegółów.
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));
        // Selenium: czekamy również aż HTMX zakończy wszystkie żądania.
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                d -> (Boolean) ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                        "return typeof htmx !== 'undefined' && !Array.from(htmx.findAll(document, '[hx-trigger]')).some(function(e) { return e.closest('.htmx-request'); })"
                ));
    }

    private WebDriver getDriver() {
        return driver;
    }
}
