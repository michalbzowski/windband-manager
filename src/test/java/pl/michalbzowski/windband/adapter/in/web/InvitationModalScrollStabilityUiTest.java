package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #279 — in the unified "Zaproś" modal, selecting a row that was only
 * reachable by scrolling jumped the view back to the top of the list.
 *
 * <p>Root cause: {@code InvitationModal._refresh()} replaces the host's whole
 * innerHTML on every toggle. The re-created {@code dialog} element is a brand
 * new node, so the browser drops both the scroll position of
 * {@code .invitation-body} and the focus on the clicked row.
 *
 * <p>This test scrolls the modal body down, clicks a row that is only visible
 * after that scroll, and asserts the modal keeps its scroll position and the
 * clicked row stays where it was on screen.
 */
class InvitationModalScrollStabilityUiTest extends UiTestBase {

    @Test
    void selectingRowAfterScrollKeepsScrollPosition() throws InterruptedException {
        // createRehearsalViaApi fires an XHR from inside the browser, so the
        // session must already be logged in and on a real page.
        loginOnly();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Seed enough members to make the modal body scrollable.
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 25; i++) {
            createTestBand1Member("Scroll" + i + uid, "Row" + uid, null);
        }
        Long rehearsalId = createRehearsalViaApi("Scroll " + uid,
                java.time.LocalDate.now().plusDays(3));
        assertThat(rehearsalId).isNotNull();

        loginAndNavigateTo("/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-btn")));

        js.executeScript("document.getElementById('open-invite-btn').click();");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".invitation-row")));

        // Make sure the body really scrolls; otherwise the assertion is vacuous.
        Long scrollable = (Long) js.executeScript(
                "var b=document.querySelector('.invitation-body');" +
                "return b ? (b.scrollHeight - b.clientHeight) : 0;");
        assertThat(scrollable).as("modal body is scrollable with 25+ members").isGreaterThan(20L);

        // Scroll down and remember which row is on screen at that offset.
        js.executeScript("document.querySelector('.invitation-body').scrollTop = 260;");
        Number beforeTop = (Number) js.executeScript(
                "return document.querySelector('.invitation-body').scrollTop;");
        assertThat(beforeTop.intValue()).as("scroll actually moved before the click").isGreaterThan(100);

        String anchor = (String) js.executeScript(
                "var rows=[].slice.call(document.querySelectorAll('.invitation-row[data-kind=member]'));" +
                "var vis=rows.filter(function(r){var b=r.getBoundingClientRect(); return b.top>0 && b.bottom<window.innerHeight;});" +
                "return vis.length ? vis[vis.length-1].getAttribute('data-id') : null;");
        assertThat(anchor).as("at least one row is visible after scrolling").isNotNull();

        // Remember where the anchor row sits ON SCREEN, then click it. The
        // user-visible definition of the bug is "the list jumps": the very same
        // row must still be at the very same screen Y after the toggle.
        // Rows are located by attribute (not by a CSS string) because numeric
        // data-ids cannot be quoted safely through Java -> JS -> CSS.
        String locateRow =
                "var id=arguments[0];" +
                "var rows=[].slice.call(document.querySelectorAll('.invitation-row'));" +
                "var r=null;" +
                "for (var i=0;i<rows.length;i++){" +
                "  if (rows[i].getAttribute('data-kind')==='member' && rows[i].getAttribute('data-id')===id) { r=rows[i]; break; }" +
                "}";
        // The dialog opens with a CSS animation, so a single read can catch the
        // row mid-flight and masquerade as a "jump". Poll until its screen Y is
        // stable, and only then use it as the reference point.
        Number yBefore = waitForStableRowY(js, locateRow, anchor);
        assertThat(yBefore.intValue())
                .as("anchor row was measurable before the click").isNotEqualTo(-1);

        js.executeScript(locateRow + " if (r) r.click();", anchor);

        // Toggle must register AND the list must not jump back to the top.
        wait.until(d -> {
            WebElement row = d.findElement(By.cssSelector(".invitation-row[data-kind='member'][data-id='" + anchor + "']"));
            return "true".equals(row.getAttribute("aria-checked"));
        });
        Number afterTop = (Number) js.executeScript(
                "var b=document.querySelector('.invitation-body'); return b ? b.scrollTop : -1;");
        assertThat(afterTop.intValue())
                .as("modal list keeps its scroll position after selecting a row")
                .isGreaterThan(100);

        Number yAfter = (Number) js.executeScript(
                locateRow + " return r ? r.getBoundingClientRect().top : -1;", anchor);
        // Tolerance is 15px, not 0: ticking a row swaps in the checkmark, which
        // re-lays-out the row box by a few pixels. The reported bug is the list
        // jumping back to the TOP (~350px here), so anything of that order must
        // fail this assertion while a sub-centimetre settle does not.
        assertThat(Math.abs(yAfter.doubleValue() - yBefore.doubleValue()))
                .as("clicked row stays at the same screen position (no jump) — was %s, became %s",
                        yBefore, yAfter)
                .isLessThanOrEqualTo(15.0);
    }

    /**
     * Polls the row's on-screen top until two reads taken ~150 ms apart agree
     * within one pixel, i.e. until any open animation has settled.
     */
    private Number waitForStableRowY(JavascriptExecutor js, String locateRow, String id)
            throws InterruptedException {
        double previous = -1;
        for (int attempt = 0; attempt < 30; attempt++) {
            Number y = (Number) js.executeScript(
                    locateRow + " return r ? r.getBoundingClientRect().top : -1;", id);
            double current = y.doubleValue();
            if (current >= 0 && Math.abs(current - previous) < 1.0) {
                return y;
            }
            previous = current;
            Thread.sleep(150);
        }
        return (Number) js.executeScript(
                locateRow + " return r ? r.getBoundingClientRect().top : -1;", id);
    }
}
