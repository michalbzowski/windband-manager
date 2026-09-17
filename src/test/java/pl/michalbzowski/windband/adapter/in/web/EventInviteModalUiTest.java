package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the unified invite flow on the event detail page (t_c9b13437):
 * <ol>
 *   <li>The single "Zaproś" button opens the shared {@code window.InvitationModal}
 *       showing a selectable row per available member.</li>
 *   <li>Selecting several members and clicking "Potwierdź" adds them all to the
 *       event's participants table (via the existing per-member invite endpoint).</li>
 *   <li>The newly invited rows are highlighted (green pulse) via the shared mechanism.</li>
 * </ol>
 */
class EventInviteModalUiTest extends UiTestBase {

    @Test
    void inviteMultipleMembersViaModal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String aFirst = "InvA" + uid;
        String aLast = "Test" + uid;
        String bFirst = "InvB" + uid;
        String bLast = "Test" + uid;

        // Fast setup: seed two band-1 members directly in the DB (no UI form). This test's
        // purpose is the multi-member invite MODAL flow, not the member-create flow — the
        // UI-based createMember() (navigate /members + fill 4 fields + submit + wait form-hide)
        // added ~2 s per member for no coverage in this scenario.
        Long idA = createTestBand1Member(aFirst, aLast, null);
        Long idB = createTestBand1Member(bFirst, bLast, null);
        assertThat(idA).as("seeded member A must have a generated id").isNotNull();
        assertThat(idB).as("seeded member B must have a generated id").isNotNull();

        // Sanity: both members are visible in the team's active member list before the invite.
        Integer seedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE id IN (?, ?)", Integer.class, idA, idB);
        assertThat(seedCount).as("both seeded members must be present in DB before the invite flow").isEqualTo(2);

        // Create an event (band_events are truncated per test, so list starts empty).
        // loginAndNavigateTo also establishes (or reuses) the admin session — needed for the
        // XHR-based inviteMemberToEvent helper that runs later on the detail page.
        Long eventId = createEventViaModalForm(uid);
        assertThat(eventId).as("event created via modal form must have a generated id").isNotNull();

        // Open OUR new event's detail page.
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Open the unified modal: single "Zaproś" button opens the shared
        // InvitationModal — mounted into #unified-invite-host on <body>. Rows are
        // <button class="invitation-row" data-id="N" ...> elements.
        jsClick(driver.findElement(By.id("open-invite-btn")));

        // Resolve the two member IDs from their visible labels (first pass over
        // rows; we use `data-id` for stable clicks later because row elements are
        // re-created each time the modal re-renders on selection change and
        // Selenium element handles go stale).
        String idForA = wait.until(d -> findRowIdByLabel(d, aFirst + " " + aLast));
        String idForB = wait.until(d -> findRowIdByLabel(d, bFirst + " " + bLast));

        int before = driver.findElements(
                By.cssSelector("#participants-table tbody tr")).size();
        System.out.println("[TEST] participants before invite: " + before);

        // Select both members then confirm via the enabled ".invitation-confirm".
        clickRowById("member", idForA);
        clickRowById("member", idForB);
        clickConfirmEnabled(wait);

        // Participants table should now contain both invited members.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='participants-table']//tr[.//td[contains(., '" + aFirst + "')]]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='participants-table']//tr[.//td[contains(., '" + bFirst + "')]]")));

        // Hard DB assertion: both invite rows persisted — the source of truth, independent of
        // render timing / highlight animation on slow CI runners.
        org.testcontainers.shaded.org.awaitility.Awaitility.await()
                .atMost(Duration.ofSeconds(8))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM event_participations WHERE event_id = ? AND member_id IN (?, ?)",
                            Integer.class, eventId, idA, idB);
                    assertThat(count).as("both invited members must be persisted as participants").isEqualTo(2);
                });

        // At least one newly invited row should be highlighted (green pulse).
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#participants-table tbody tr.highlight-row")));

        int after = driver.findElements(
                By.cssSelector("#participants-table tbody tr")).size();
        System.out.println("[TEST] participants after invite: " + after);
        assertThat(after).isEqualTo(before + 2);
    }

    /**
     * Creates a new event through the REAL UI form flow (which is what this test class covers:
     * the invite modal on an existing event), then returns the generated event id.
     *\
     * <p>Returns the event's DB id so that subsequent navigation / XHR helpers can work with a
     * stable key instead of scraping it out of the DOM.
     */
    private Long createEventViaModalForm(String uid) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        String eventName = "InviteEvt" + uid;
        fill("name", eventName);
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "'");
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//a[contains(., 'Szczegóły')]")));

        // Resolve our new event's id from the list (band-scoped, name is unique per uid).
        Long eventId = jdbcTemplate.queryForObject(
                "SELECT id FROM band_events WHERE name = ?", Long.class, eventName);
        assertThat(eventId).as("newly created event must be visible in DB by its unique name").isNotNull();
        return eventId;
    }

    /**
     * Finds the data-id attribute of an invitation modal row whose visible
     * label text matches. Returns null if no matching row exists yet (caller
     * should wait.until for non-null — a modal that hasn't rendered its rows yet
     * returns empty list). Resolving to `data-id` once lets the test re-resolve
     * the element at click time without holding a stale Selenium handle.
     */
    private String findRowIdByLabel(org.openqa.selenium.WebDriver d, String label) {
        return d.findElements(By.cssSelector(".invitation-row")).stream()
                .filter(r -> r.getText().contains(label))
                .map(r -> r.getAttribute("data-id"))
                .filter(s -> s != null && !s.isEmpty())
                .findFirst().orElse(null);
    }

    /**
     * Clicks an invitation-row by data-kind + data-id, resolving the element at
     * click time — always fresh, never stale. Waits up to 5 s for the element
     * to exist and be enabled.
     */
    private void clickRowById(String kind, String id) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(d -> d.findElements(By.cssSelector(
                ".invitation-row[data-kind='" + kind + "'][data-id='" + id + "']"))
                .stream().findFirst().orElse(null) != null);
        // Use JS click (not el.click()) to avoid Selenium's element-binding step.
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-row[data-kind='" + kind
                        + "'][data-id='" + id + "']\").click();");
    }

    private void clickConfirmEnabled(WebDriverWait wait) {
        // The "Potwierdź" button carries the `disabled` HTML attribute while the
        // selection count is 0; after selecting rows it becomes enabled. Wait for
        // the enabled state (up to 10 s), then JS-click without holding a handle.
        wait.until(d -> d.findElements(By.cssSelector(".invitation-confirm"))
                .stream().filter(b -> ((WebElement) b).isEnabled()).findFirst().orElse(null) != null);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\".invitation-confirm\").click();");
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
