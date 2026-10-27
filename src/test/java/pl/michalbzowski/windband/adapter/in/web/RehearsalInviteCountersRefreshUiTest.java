package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #272 — after inviting people to a rehearsal, the attendance counters
 * (and the invited list) must refresh WITHOUT a manual page reload.
 *
 * <p>Root cause found while writing this test: {@code refreshContent()} in
 * {@code rehearsals/detail.html} fired {@code GET /rehearsals/{id}/list}, but no
 * such route exists in {@code RehearsalPageController} (only {@code /list} for
 * the collection page and {@code /{id}} for the detail fragment). The HTMX call
 * therefore answered 404, no swap happened and the page kept showing the stale
 * "Brak zaproszonych uczestników" state and the stale {@code
 * #attendance-response-filter-container} counters.
 *
 * <p>This test asserts the user-visible contract only: after confirming the
 * unified invite modal, the attendance table row and the "no response" counter
 * must appear/update in the live DOM. It never reloads the page, so it fails on
 * the broken endpoint and passes once the refresh targets a real route.
 */
class RehearsalInviteCountersRefreshUiTest extends UiTestBase {

    @Test
    void invitedMembersAndUpdateCountersWithoutPageReload() {
        // Order matters: createRehearsalViaApi runs an XHR inside the browser,
        // so it needs a loaded, authenticated page first.
        loginOnly();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Long memberId = createTestBand1Member("Counter" + uid, "Refresh" + uid, null);
        Long rehearsalId = createRehearsalViaApi("Refresh " + uid, LocalDate.now().plusDays(2));
        assertThat(rehearsalId).isNotNull();

        loginAndNavigateTo("/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Baseline: nobody invited yet — no attendance list, no counter container.
        assertThat(driver.findElements(By.cssSelector("#rehearsals-content table[role='grid'] tbody tr")))
                .as("no invited members before the invite")
                .isEmpty();

        // Open the unified invite modal and pick the member by data-id.
        js.executeScript("document.getElementById('open-invite-btn').click();");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));
        js.executeScript("var r=document.querySelector('.invitation-row[data-kind=\"member\"][data-id=\""
                + memberId + "\"]'); if(r) r.click();");

        WebElement confirm = wait.until(d -> {
            List<WebElement> all = d.findElements(By.cssSelector(".invitation-confirm"));
            return all.stream().filter(WebElement::isEnabled).findFirst().orElse(null);
        });
        js.executeScript("arguments[0].click();", confirm);

        // The invited member must appear in the attendance table WITHOUT a reload.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<WebElement> rows = driver.findElements(
                    By.cssSelector("#rehearsals-content table[role='grid'] tbody tr[data-member-id]"));
            assertThat(rows)
                    .as("attendance table lists the newly invited member without page reload")
                    .anyMatch(r -> String.valueOf(memberId).equals(r.getAttribute("data-member-id")));
        });

        // And the status counter (no response) must update in the live DOM.
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            WebElement counter = driver.findElement(By.cssSelector(".filter-count-no-response"));
            assertThat(counter.getText().trim())
                    .as("'no response' counter reflects the freshly invited member")
                    .isEqualTo("1");
        });
    }
}
