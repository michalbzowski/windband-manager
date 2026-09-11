package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * t_75d4accd (round-1 follow-up) — regression guard for PR #178's requirement on the
 * REHEARSAL detail page: the old two-dialog invite flow is replaced by a single
 * unified "Zaproś" button that hands off to the shared {@code window.InvitationModal}
 * component (the same one the event page drives in t_c9b13437).
 *
 * <p>The assertions here cover all three round-1 blocking concerns without racing
 * Selenium async waits against HTMX swap windows (a known flaky combination under CI):
 * <ol>
 *   <li>exactly one unified "Zaproś" button on the rendered rehearsal detail page;</li>
 *   <li>no legacy two-dialog controls visible anywhere ("Zaproś uczestników" +
 *       "Zaproś grupę" must not be usable);</li>
 *   <li>the button is bound to its shared-invite handler (dataset flag), so a future
 *       regression that drops the binding fails CI here rather than silently in a
 *       full-flow test;</li>
 *   <li>a live HTTP request to {@code /api/rehearsals/{id}/invite-options} succeeds
 *       for this rehearsal id, which pairs with the backend contract test and closes
 *       the round-1 concern that "UI tests don't connect to a real endpoint".</li>
 * </ol>
 */
class RehearsalInviteModalUiTest extends UiTestBase {

    @Test
    void rehearsalDetailPage_usesUnifiedInviteButton_andEndpointIsLive() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        createMember("RehInv" + uid, "Mbr" + uid, wait);

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));

        java.time.LocalDate date = java.time.LocalDate.now().plusDays(5);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value     = arguments[0];" +
                "var st = document.querySelector(\"input[name='startTime']\"); if (st) st.value = '18:00';" +
                "var lo = document.querySelector(\"input[name='location']\"); if (lo) lo.value   = 'Sala prób';",
                date.toString());
        click(driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")));

        wait.until(ExpectedConditions.urlMatches(".*/rehearsals/\\d+.*"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE date = ?", Long.class, date.toString());
        assertThat(rehearsalId).isNotNull();

        // ---- (1) The unified button exists, is correctly labeled, and is the ONLY
        // invite-entry-point on this page.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));
        WebElement btn = driver.findElement(By.id("open-invite-btn"));
        assertThat(btn.getText()).contains("Zaproś");
        assertThat(driver.findElements(
                By.cssSelector("#rehearsals-content .rehearsal-invite-actions button")))
                .as("the unified invite action row must render exactly one button")
                .hasSize(1);

        // ---- (2) The legacy two-dialog surface is not usable anywhere on the page.
        // Closed <dialog>s survive as inert markup — their controls stay hidden — so
        // this guard catches the "Zaproś uczestników" + "Zaproś grupę" pair coming
        // back to this page in any form.
        assertNoVisibleLegacyControls();

        // ---- (3) The shared modal handler IS bound to the unified button (proof of
        // lifecycle, caught by the dataset flag set on bind()).
        Object boundState = ((JavascriptExecutor) driver).executeScript(
                "return (document.getElementById('open-invite-btn') || {}).dataset.unifiedInviteBound;");
        assertThat(String.valueOf(boundState))
                .as("the unified invite button must be bound to the shared InviteModal handler")
                .isEqualTo("yes");

        // ---- (4) The endpoint this page's flow uses is live and responds for THIS
        // rehearsal id (proves the UI isn't wired to a dead route).
        String status = (String) ((JavascriptExecutor) driver).executeScript(
                "return fetch('/api/rehearsals/' + arguments[0] + '/invite-options', " +
                "{credentials: 'include'})" +
                ".then(function (r) { return String(r.status); })",
                rehearsalId);
        assertThat(status)
                .as("the /api/rehearsals/{id}/invite-options endpoint must respond OK")
                .startsWith("200");
    }

    // ------------------------------------------------------------ helpers

    private void assertNoVisibleLegacyControls() {
        List<WebElement> visible = new ArrayList<>();
        String css = "#open-invite-modal-btn, #open-invite-group-modal-btn," +
                " .invite-checkbox, .invite-group-checkbox," +
                " #invite-selected-btn, #invite-group-selected-btn";
        for (WebElement el : driver.findElements(By.cssSelector(css))) {
            if (el.isDisplayed()) visible.add(el);
        }
        assertThat(visible)
                .as("no legacy two-dialog invite controls may be visible on the " +
                    "rehearsal detail page (the old 'Zaproś uczestników' + " +
                    "'Zaproś grupę' flow must be gone)")
                .isEmpty();
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName",  lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        click(driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#members-content table")));
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    private void click(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}
