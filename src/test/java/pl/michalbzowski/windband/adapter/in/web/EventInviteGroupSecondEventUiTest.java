package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
 * re-execute the page-level script), so #invite-group-btn had no handler on the
 * second event. This test navigates via HTMX ("Szczegóły") across two events and
 * confirms, through the unified invite modal (t_c9b13437), that the handlers are
 * re-bound after every swap — proving both invites persist.
 */
class EventInviteGroupSecondEventUiTest extends UiTestBase {

    @Test
    void inviteSameGroupToTwoEventsViaHtmxNavigation() throws Exception {
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
        String groupIdStr = (String) ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open('POST', '/api/groups', false);"
                        + "xhr.setRequestHeader('Content-Type', 'application/json');"
                        + "xhr.send(JSON.stringify({name: '" + groupName + "', description: 'test'}));"
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
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-" + eventId1)));
        clickSzczegoly(eventId1);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Open the unified modal, select the group row, confirm
        openUnifiedModal(wait);
        String groupIdAttr1 = findGroupRowId(wait, groupName);
        clickRowAndConfirm(groupIdAttr1);

        // Event 1 should now have both members. Poll the JDBC connection for the
        // persisted event_participations rows — the source of truth, independent of
        // render timing on slow CI runners.
        assertEventHasMembers(eventId1, alphaId, betaId);

        // ---- Navigate BACK to list (click "Powrót" — HTMX) then to EVENT 2 via HTMX.
        // This is the core regression: after two HTMX navigations the modal handler
        // must still be bound on the second event detail page.
        clickPowrot();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-" + eventId2)));
        clickSzczegoly(eventId2);
        // Wait for HTMX to settle after navigation so handlers are re-bound
        waitForHtmxSettle();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Invite the SAME group to the second event (via unified modal)
        openUnifiedModal(wait);
        String groupIdAttr2 = findGroupRowId(wait, groupName);
        clickRowAndConfirm(groupIdAttr2);

        // Event 2 should now have both members (this is the reported bug). Same
        // JDBC poll as event 1.
        assertEventHasMembers(eventId2, alphaId, betaId);
    }

    private void openUnifiedModal(WebDriverWait wait) {
        jsClick(driver.findElement(By.id("open-invite-btn")));
        // The modal renders selection rows on demand; wait for at least one row.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));
    }

    private void clickSzczegoly(Long eventId) {
        WebElement row = driver.findElement(By.id("event-" + eventId));
        WebElement btn = row.findElement(By.xpath(".//a[contains(., 'Szczegóły')]"));
        jsClick(btn);
    }

    private void clickPowrot() {
        // The "Powrót" link on event/rehearsal detail pages is the
        // unified detail-actions-bar back icon (fragments/detail-page-actions-bar.html).
        WebElement back = driver.findElement(By.cssSelector(".detail-actions-bar .detail-back-link"));
        jsClick(back);
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'}); arguments[0].click();", el);
    }

    private String findGroupRowId(WebDriverWait wait, String label) {
        return wait.until(d -> d.findElements(By.cssSelector(".invitation-row[data-kind='group']")).stream()
                .filter(r -> r.getText().contains(label))
                .map(r -> r.getAttribute("data-id"))
                .filter(s -> s != null && !s.isEmpty())
                .findFirst().orElse(null));
    }

    private void clickRowAndConfirm(String groupIdAttr) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        // Click the group row via CSS selector (re-resolves at click time — never stale).
        wait.until(d -> d.findElements(By.cssSelector(
                        ".invitation-row[data-kind='group'][data-id='" + groupIdAttr + "']"))
                .stream().findFirst().orElse(null) != null);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-row[data-kind='group'][data-id='" + groupIdAttr + "']\").click();");

        // Wait for the confirm button to be enabled, then click via a fresh resolver.
        wait.until(d -> d.findElements(By.cssSelector(".invitation-confirm")).stream()
                .filter(WebElement::isEnabled).findFirst().orElse(null) != null);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-confirm\").click();");
    }

    private void assertEventHasMembers(Long eventId, Long alphaId, Long betaId) {
        // Poll the database for the participants — much more reliable than re-rendering
        // the event detail page and parsing HTML on a slow CI runner.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
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
        String eventIdStr = (String) ((JavascriptExecutor) driver).executeScript(
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
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("member-form")));
        fillField("firstName", first);
        fillField("lastName", last);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
    }

    private void addMemberToGroupViaApi(WebDriver driver, Long groupId, Long memberId) {
        ((JavascriptExecutor) driver).executeScript(
                "return fetch('/api/groups/' + arguments[0] + '/members/' + arguments[1], {"
                        + "  method: 'POST'"
                        + "});", groupId, String.valueOf(memberId));
    }

    private void loginAsAdmin(WebDriver driver, WebDriverWait wait) {
        driver.get(baseUrl() + "/login");
        driver.findElement(org.openqa.selenium.By.name("username")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.name("password")).sendKeys("admin");
        driver.findElement(org.openqa.selenium.By.cssSelector("button[type='submit']")).click();
        wait.until(ExpectedConditions.not(
                ExpectedConditions.urlContains("/login")));
    }

    private void fillField(String name, String value) {
        WebElement el = driver.findElement(By.name(name));
        el.clear();
        el.sendKeys(value);
    }

    private void waitForHtmxSettle() {
        // Wait for the event detail fragment and the unified button to be present.
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));
        // Wait until HTMX stops making in-flight (hx-trigger) requests so the swap
        // — and the handler re-binding via the page's MutationObserver — is complete.
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
                d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                        "return typeof htmx !== 'undefined' && !Array.from(htmx.findAll(document, '[hx-trigger]')).some(function(e) { return e.closest('.htmx-request'); })"
                ));
    }

    private WebDriver getDriver() {
        return driver;
    }
}
