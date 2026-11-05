package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #280 — when inviting people, clicking "Potwierdź" gave no feedback at
 * all: the modal closed immediately while the invite POSTs were still running
 * in the background, so the user could not tell whether anything happened.
 *
 * <p>Expected behaviour (from the report: "chciałbym widzieć jakiegoś rodzaju
 * wskaźnik, loadera"): while invitations are in flight the modal stays open in
 * a busy state showing a loader, the confirm action cannot fire a second round,
 * and the dialog closes only once the server has answered.
 *
 * <p>The invite POSTs are delayed from inside the page so the in-flight window
 * is long enough to observe deterministically — the fix itself uses no timers.
 */
class InvitationModalBusyFeedbackUiTest extends UiTestBase {

    /** Locates a member row by attribute; avoids Java->JS->CSS quote juggling. */
    private static final String LOCATE_MEMBER_ROW =
            "var id=arguments[0];" +
            "var rows=[].slice.call(document.querySelectorAll('.invitation-row'));" +
            "var r=null;" +
            "for (var i=0;i<rows.length;i++){" +
            "  if (rows[i].getAttribute('data-kind')==='member' && rows[i].getAttribute('data-id')===id) { r=rows[i]; break; }" +
            "}";

    @Test
    void confirmKeepsModalOpenWithBusyIndicatorUntilServerAnswers() {
        // The invite POST runs inside the browser session, so log in first.
        loginOnly();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Long memberId = createTestBand1Member("Busy" + uid, "Feedback" + uid, null);
        Long rehearsalId = createRehearsalViaApi("Busy " + uid, LocalDate.now().plusDays(4));
        assertThat(rehearsalId).isNotNull();

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        // Slow down ONLY the invite POSTs (never invite-options) so that the
        // in-flight state is observable.
        js.executeScript(
                "window.__inviteStatuses = [];" +
                "var orig = window.fetch;" +
                "window.fetch = function(url, opts) {" +
                "  var u = String(url || '');" +
                "  var isInvitePost = opts && opts.method === 'POST' && u.indexOf('/invite') !== -1" +
                "      && u.indexOf('invite-options') === -1;" +
                "  if (!isInvitePost) { return orig.apply(this, arguments); }" +
                "  var p = orig.apply(this, arguments);" +
                "  window.__invitePending = (window.__invitePending || 0) + 1;" +
                "  return new Promise(function(resolve) {" +
                "    setTimeout(function(){ p.then(function(res) {" +
                "      window.__inviteStatuses.push(res.status);" +
                "      window.__invitePending--;" +
                "      resolve(res);" +
                "    }); }, 2500);" +
                "  });" +
                "};");

        js.executeScript("document.getElementById('open-invite-btn').click();");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));
        js.executeScript(LOCATE_MEMBER_ROW + " if (r) r.click();", String.valueOf(memberId));

        // The selection must register before we can talk about confirming.
        wait.until(d -> {
            WebElement row = d.findElement(
                    By.cssSelector(".invitation-row[data-kind='member'][data-id='" + memberId + "']"));
            return "true".equals(row.getAttribute("aria-checked"));
        });
        WebElement confirm = wait.until(d -> {
            List<WebElement> all = d.findElements(By.cssSelector(".invitation-confirm"));
            return all.stream().filter(WebElement::isEnabled).findFirst().orElse(null);
        });

        js.executeScript("arguments[0].click();", confirm);

        // 1. The dialog stays OPEN while the request is in flight...
        Boolean dialogOpen = (Boolean) js.executeScript(
                "var d=document.querySelector('#unified-invite-host dialog');" +
                "return !!d && d.open === true;");
        assertThat(dialogOpen).as("modal stays open while inviting").isTrue();

        // ...and exposes a VISIBLE busy indicator (loader / status text).
        WebElement busy = wait.until(d -> {
            List<WebElement> c = d.findElements(By.cssSelector(
                    ".invitation-busy, [data-invitation-busy], .invitation-confirm[aria-busy='true']"));
            return c.isEmpty() ? null : c.get(0);
        });
        assertThat(js.executeScript(
                "var e=arguments[0];var r=e.getBoundingClientRect();" +
                "return r.width>0 && r.height>0;", busy))
                .as("busy indicator is actually visible on screen")
                .isEqualTo(true);

        // 2. Confirm must not be re-clickable while busy (no duplicate invites).
        Object confirmState = js.executeScript(
                "var b=document.querySelector('.invitation-confirm');" +
                "return b ? String(!!b.disabled) + '/' + String(b.getAttribute('aria-disabled')) : 'missing';");
        assertThat(String.valueOf(confirmState))
                .as("confirm button is disabled while the invite is in flight")
                .contains("true");

        js.executeScript("var b=document.querySelector('.invitation-confirm'); if(b) b.click();");

        // 3. Once the server answered, the modal closes and the loader is gone.
        wait.until(d -> {
            Object open = js.executeScript(
                    "var d=document.querySelector('#unified-invite-host dialog'); return d && d.open === true;");
            return !Boolean.TRUE.equals(open);
        });
        assertThat(js.executeScript("return window.__inviteStatuses.length;"))
                .as("exactly one invite request per selected member (no double submit)")
                .isEqualTo(1L);
        assertThat(js.executeScript("return String(window.__inviteStatuses);"))
                .as("invite POST succeeded")
                .isEqualTo("200");
    }
}
