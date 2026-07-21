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
        String groupIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return fetch('/api/groups', {"
                        + "  method: 'POST', headers: {'Content-Type':'application/json'},"
                        + "  body: JSON.stringify({name: 'GrupaTest" + uid + "', description: 'test'})"
                        + "}).then(r => r.json()).then(g => '' + g.id);");
        Thread.sleep(800);
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
        selectAndInviteGroupModal(wait, groupId);

        // Event 1 should now have both members.
        // Use the proven full-page-nav pattern (same as EventInviteGroupUiTest) instead of
        // trusting the in-page HTMX outerHTML reload after the invite POST: CI on Ubuntu is
        // slower than local Fedora and htmx swap occasionally settles before the new
        // participants fragment is in place, causing a 15s Awaitility timeout. A fresh
        // `driver.get(...)` bypasses the HTMX race and verifies the server-side result.
        assertEventHasMembers(driver, wait, eventId1, alphaId, betaId);

        // ---- Navigate BACK to list (full page load — the detail page loaded above has no
        // #events-list-container, so the in-page "Powrót" HTMX target is missing here),
        // then to EVENT 2 via HTMX ----
        driver.get(baseUrl() + "/events");
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
        selectAndInviteGroupModal(wait, groupId);

        // Event 2 should now have both members (this is the reported bug). Same
        // full-page-nav assert pattern as event 1 — reliable on CI.
        assertEventHasMembers(driver, wait, eventId2, alphaId, betaId);
    }

    private void clickSzczegoly(WebDriverWait wait, Long eventId) {
        WebElement row = driver.findElement(By.id("event-" + eventId));
        WebElement btn = row.findElement(By.xpath(".//button[contains(text(),'Szczegóły')]"));
        jsClick(btn);
    }

    private void clickPowrot(WebDriverWait wait) {
        WebElement back = driver.findElement(By.xpath("//button[contains(text(),'Powrót')]"));
        jsClick(back);
    }

    private void selectAndInviteGroupModal(WebDriverWait wait, Long groupId) {
        driver.findElement(By.xpath(
                "//*[@id='invite-group-modal']//label[contains(text(), 'GrupaTest')]/preceding-sibling::input[@type='checkbox']"))
                .click();
        jsClick(driver.findElement(By.id("invite-group-selected-btn")));
        // Give HTMX reload time to settle - longer wait for CI
        try { Thread.sleep(5000); } catch (InterruptedException ignored) { /* intentionally ignored */ }
        // Additionally wait for any pending HTMX requests to complete
        waitForHtmxSettle();
    }

    private void jsClick(WebElement el) {
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el);
    }

    private void assertEventHasMembers(WebDriver driver, WebDriverWait wait,
                                        Long eventId, Long alphaId, Long betaId) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            // Full-page reload to read the server-rendered table — avoids relying on the
            // in-page HTMX outerHTML swap that occasionally loses the new participants
            // fragment in slow CI environments.
            driver.get(baseUrl() + "/events/" + eventId);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
            var rows = driver.findElements(
                    By.cssSelector("#participants-table tbody tr[data-member-id]"));
            assertThat(rows)
                    .as("Both group members should appear as participants after inviting the group")
                    .hasSizeGreaterThanOrEqualTo(2);
            boolean hasAlpha = rows.stream().anyMatch(
                    r -> r.getAttribute("data-member-id").equals(String.valueOf(alphaId)));
            boolean hasBeta = rows.stream().anyMatch(
                    r -> r.getAttribute("data-member-id").equals(String.valueOf(betaId)));
            assertThat(hasAlpha).isTrue();
            assertThat(hasBeta).isTrue();
        });
    }

    private Long createEventViaApi(WebDriver driver, String name, Long bandId) {
        String eventIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "return fetch('/api/events', {"
                        + "  method: 'POST', headers: {'Content-Type':'application/json'},"
                        + "  body: JSON.stringify({name: 'Wydarzenie" + name + "', date: '" + LocalDate.now() + "',"
                        + "    startTime: '18:00', endTime: '20:00', paymentType: 'FREE', eventType: 'CONCERT', bandId: " + bandId + "})"
                        + "}).then(r => r.json()).then(ev => '' + ev.id);");
        try { Thread.sleep(500); } catch (InterruptedException ignored) { /* intentionally ignored */ }
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
        try { Thread.sleep(800); } catch (InterruptedException ignored) { /* noop */ }
        // Additional wait for any pending HTMX requests
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(
                d -> (Boolean) ((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                        "return typeof htmx !== 'undefined' && !Array.from(htmx.findAll(document, '[hx-trigger]')).some(function(e) { return e.closest('.htmx-request'); })"
                ));
    }

    private WebDriver getDriver() {
        return driver;
    }
}
