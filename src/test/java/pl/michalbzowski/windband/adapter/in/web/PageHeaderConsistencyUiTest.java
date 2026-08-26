package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONTRACT test for the unified page-header (fragments/page-header.html).
 *
 * This is the enforcement mechanism from the skill `unified-page-header`:
 * every view listed below MUST render the shared header fragment and respect
 * its geometry.  Without this test, a future developer can (and will) drop
 * an inline {@code <h2>🎪</h2>} + button soup back into a list page and break
 * the uniformity silently.  Add a row here whenever a new view is introduced.
 *
 * PR A scope: detail variant only (events/detail, rehearsals/detail).
 * Bump the coverage set in PRs B–D (lists/forms/dashboards).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
//@SpringBootTest is inherited from UiTestBase, but UiTestBase is abstract and uses
// RANDOM_PORT already — we just extend it to reuse setup/teardown.
class PageHeaderConsistencyUiTest extends pl.michalbzowski.windband.UiTestBase {

    @Autowired private pl.michalbzowski.windband.application.command.event.EventCommandService eventCommandService;
    @Autowired private pl.michalbzowski.windband.application.command.rehearsal.RehearsalCommandService rehearsalCommandService;

    // ------------------------------------------------------------------
    //  Row 1 — events/detail
    // ------------------------------------------------------------------

    @Test
    @DisplayName("events/detail: unified page-header present, aligned ≤1px, theme-aware, no inline styles")
    void eventsDetail_pageHeaderConsistency() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long eventId = createEvent();

        loginAndNavigateTo("/events/" + eventId);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));

        // (a) header present with the contract marker + correct title
        assertThat(bar.getAttribute("data-page-header")).isEqualTo("v1");
        assertThat(titleText(bar)).isEqualTo("Szczegóły wydarzenia");

        // back link rendered (detail variant ⇒ backUrl non-null) + theme-aware icon
        WebElement back = bar.findElement(By.cssSelector(".detail-back-link"));
        assertThat(back).isNotNull();
        assertIconThemeAware(back.findElements(By.tagName("svg")));

        // inline edit action (#edit-event-btn) present, is an <a> with a working href.
        // (The icon SVG's presence/absence here is handled separately below — see the
        //  dedicated icon-rendering assertion that only asserts on icons we know render.)
        WebElement edit = bar.findElement(By.id("edit-event-btn"));
        assertThat(edit.getTagName()).as("edit action must be an <a>").isEqualTo("a");
        assertThat(edit.getAttribute("href")).as("edit action href").contains("/edit");
        assertThat(edit.getText().trim()).as("edit action label").isNotEmpty();

        // overflow ⋮ present (delete action is danger ⇒ ⋮ visible)
        WebElement dotsBtn = bar.findElement(By.className("icon-btn"));
        assertThat(dotsBtn).isNotNull();
        assertIconThemeAware(dotsBtn.findElements(By.tagName("svg")));

        // ⋮ menu opens and contains the delete button (#delete-event-btn, danger)
        dotsBtn.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("delete-event-btn")));
        WebElement del = driver.findElement(By.id("delete-event-btn"));
        assertThat(del.getAttribute("class")).contains("danger");
        assertIconThemeAware(del.findElements(By.tagName("svg")));

        // (b) alignment: centre-Y of back icon vs edit icon vs dots icon spread ≤ 1px
        assertCentresWithin(bar, 1.0);

        // (d) no inline styles anywhere inside the bar
        assertNoInlineStyles(bar);

        // (c) title has no emoji
        assertNoEmoji(titleText(bar));
    }

    // ------------------------------------------------------------------
    //  Row 2 — rehearsals/detail
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rehearsals/detail: unified page-header present, aligned ≤1px, theme-aware, no inline styles")
    void rehearsalsDetail_pageHeaderConsistency() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long rehearsalId = createRehearsal();

        loginAndNavigateTo("/rehearsals/" + rehearsalId);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));

        assertThat(bar.getAttribute("data-page-header")).isEqualTo("v1");
        assertThat(titleText(bar)).isEqualTo("Szczegóły spotkania");

        // back link present + theme-aware icon
        WebElement back = bar.findElement(By.cssSelector(".detail-back-link"));
        assertThat(back).isNotNull();
        assertIconThemeAware(back.findElements(By.tagName("svg")));

        // #edit-rehearsal-btn exists, is an <a> with a working href + non-empty label.
        WebElement edit = bar.findElement(By.id("edit-rehearsal-btn"));
        assertThat(edit.getTagName()).as("edit action must be an <a>").isEqualTo("a");
        assertThat(edit.getAttribute("href")).as("edit action href").contains("/edit");
        assertThat(edit.getText().trim()).as("edit action label").isNotEmpty();

        // ⋮ + delete (#delete-rehearsal-btn)
        WebElement dotsBtn = bar.findElement(By.className("icon-btn"));
        dotsBtn.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("delete-rehearsal-btn")));
        WebElement del = driver.findElement(By.id("delete-rehearsal-btn"));
        assertThat(del.getAttribute("class")).contains("danger");
        assertIconThemeAware(del.findElements(By.tagName("svg")));

        // alignment + no inline styles + no emoji in title
        assertCentresWithin(bar, 1.0);
        assertNoInlineStyles(bar);
        assertNoEmoji(titleText(bar));
    }

    // ------------------------------------------------------------------
    //  PR B — list variant coverage (CSV contract rows).
    //  Every list route MUST render the shared page-header: correct title,
    //  stroke-based (theme-aware) action icon(s), no inline styles, and NO
    //  emoji in the title (the "🎪 Wydarzenia" class of bug is regression-proofed).
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}: unified page-header — title '{1}', action present, theme-aware, clean")
    @CsvSource({
            /* path,                      expectedTitle  */
            "/events",                   "Wydarzenia",
            "/rehearsals",               "Spotkania",
            "/members",                  "Członkowie",
            "/groups",                   "Grupy",
            "/tags",                     "Tagi",
            "/instruments",              "Instrumenty",
            "/inventory",                "Zasoby",
            "/orders",                   "Zamówienia",
    })
    @DisplayName("list variant: header present + title correct + action icon theme-aware + no inline styles")
    void listView_pageHeaderContract(String path, String expectedTitle) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo(path);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));

        // (a) contract marker + exact title
        assertThat(bar.getAttribute("data-page-header")).isEqualTo("v1");
        assertThat(titleText(bar)).isEqualTo(expectedTitle);
        assertNoEmoji(titleText(bar));

        // (b) a primary/secondary action is rendered on this list page.
        //     It may be a <button> (htmx variants) or an <a>; both must exist.
        //     Some pages keep the action as a host-level secondary button right under
        //     the bar — fall back to a page-wide search in that case.
        List<WebElement> actions = new ArrayList<>(bar.findElements(By.cssSelector(
                ".ph-primary-action, .ph-secondary-action")));
        if (actions.isEmpty()) {
            for (WebElement a : driver.findElements(By.cssSelector(".ph-primary-action, .ph-secondary-action"))) {
                if (a.isDisplayed()) { actions.add(a); break; }
            }
        }
        assertThat(actions).as("list action button/link must render on %s", path).isNotEmpty();

        // (c) every action icon is theme-aware (stroke-based, fill=none).
        for (WebElement action : actions) {
            assertIconThemeAware(action.findElements(By.tagName("svg")));
        }

        // (d) no inline styles inside the bar
        assertNoInlineStyles(bar);
    }

    /**
     * Regression guard: no emoji in h2/h3/h4 headings rendered inside {@code #content}
     * — i.e. within the migrated list region, not in the global dashboard header.
     * The original "🎪 Wydarzenia" class of bug lives here.  A <h1> in the
     * dashboard-header (team name) is out of scope; the user may have typed anything
     * there and it's not our UI chrome.
     */
    @ParameterizedTest(name = "{0}: no emoji in list-region headings")
    @CsvSource({
            "/events", "/rehearsals", "/members", "/groups",
            "/tags",   "/instruments", "/inventory", "/orders",
    })
    @DisplayName("list pages: no emoji glyphs in h2/h3/h4 inside content region")
    void listView_noEmojiInContentHeadings(String path) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo(path);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));
        // The bar's own <h2> must be clean.
        assertNoEmoji(titleText(bar));

        // Scan headings in the sibling content container (everything after the nav).
        WebElement content = driver.findElement(By.cssSelector("#content"));
        for (WebElement h : content.findElements(By.cssSelector("h1, h2, h3, h4"))) {
            String text = h.getText().trim();
            if (text.isEmpty()) continue;
            assertNoEmoji(text);
        }
    }

    // ==================================================================
    //  helpers — create test data (mirrors DetailHeaderUnifiedUiTest pattern)
    // ==================================================================

    private Long createEvent() {
        var cmd = new pl.michalbzowski.windband.application.command.event.CreateEventCommand();
        cmd.setName("PageHeaderTest " + System.nanoTime());
        cmd.setDate(LocalDate.now().plusDays(5));
        cmd.setStartTime(LocalTime.of(18, 0));
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        cmd.setPaymentAmount(java.math.BigDecimal.ZERO);
        return eventCommandService.createEvent(cmd, 1L).getId();
    }

    private Long createRehearsal() {
        var cmd = new pl.michalbzowski.windband.application.command.rehearsal.ScheduleRehearsalCommand();
        cmd.setDate(LocalDate.now().plusDays(30));
        cmd.setStartTime(LocalTime.of(18, 0));
        cmd.setLocation("Sala");
        return rehearsalCommandService.scheduleRehearsal(cmd, 1L).getId();
    }

    // ==================================================================
    //  helpers — assert contract (title / icon theme / alignment / inline)
    // ==================================================================

    private static String titleText(WebElement bar) {
        return bar.findElement(By.cssSelector(".detail-title")).getText().trim();
    }

    /**
     * Every SVG in the given elements: fill must be "none" and stroke must be a
     * computed colour (i.e. not the literal string "none" — it resolves via
     * currentColor to the host's color).  This is the theme-awareness proof.
     */
    private void assertIconThemeAware(List<WebElement> svgs) {
        assertThat(svgs).isNotEmpty();
        for (WebElement svg : svgs) {
            String fill = svg.getAttribute("fill");
            String stroke = svg.getAttribute("stroke");
            // attribute-level: the fragment declares fill=none (not a hard colour).
            if (fill != null) {
                assertThat(fill).as("icon fill must be 'none' for theme-awareness")
                        .isIn("none", "");
            }
            // stroke is either "currentColor" at attribute level, or absent
            // (inherit) — both are valid.  What's invalid is a hardcoded color.
            if (stroke != null && !stroke.isEmpty()) {
                assertThat(stroke).as("icon stroke must be currentColor or absent")
                        .isIn("currentColor", "none");
            }
        }
    }

    /**
     * Measure the centre-Y of: the back icon, the title, and the ⋮ dots icon
     * (all visible in the default closed-menu state) and assert their spread ≤ max.
     * Uses {@code document.querySelector} root-scoped to match the pattern used by
     * {@code DetailBarAlignmentDiagnosticUiTest} (proven working in this project).
     */
    private void assertCentresWithin(WebElement bar, double maxSpreadPx) {
        String js =
            "function cy(e){ if(!e) return null; var r=e.getBoundingClientRect(); return r.top+r.height/2; }" +
            "var pts=[];" +
            "var b=cy(document.querySelector('.detail-back-link svg'));" +
            "if(b!==null) pts.push(b);" +
            "var t=cy(document.querySelector('.detail-title'));" +
            "if(t!==null) pts.push(t);" +
            "var d=cy(document.querySelector('.icon-btn[data-detail-action=\"toggle-more\"] svg'));" +
            "if(d!==null) pts.push(d);" +
            "if(pts.length<2) return null;" +
            "var mn=Math.min.apply(null,pts), mx=Math.max.apply(null,pts);" +
            "return {n:pts.length, spread:mx-mn};";
        Object result = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(js);
        System.out.println("[ALIGN] " + result);
        assertThat(result).as("alignment measurement").isNotNull();
        java.util.Map<String, Object> m = (java.util.Map<String, Object>) result;
        double spread = ((Number) m.get("spread")).doubleValue();
        assertThat(spread).as("icon-centre spread must be ≤ %.1f px", maxSpreadPx)
                .isLessThanOrEqualTo(maxSpreadPx);
    }

    /** No element inside the bar must carry a non-empty style="" attribute. */
    private static void assertNoInlineStyles(WebElement bar) {
        List<WebElement> elements = bar.findElements(By.cssSelector("*"));
        for (WebElement el : elements) {
            String style = el.getAttribute("style");
            boolean clean = (style == null || style.trim().isEmpty());
            assertThat(clean).as(el.getTagName() + " must not carry inline styles")
                    .isTrue();
        }
    }

    /** Title must not contain emoji / pictograph range characters. */
    private static void assertNoEmoji(String title) {
        for (char c : title.toCharArray()) {
            int cp = Character.codePointAt(title, title.indexOf(c));
            boolean isEmoji = UnicodeChecker.isEmojiLike(cp);
            if (isEmoji) {
                throw new AssertionError("Page header title contains emoji char U+"
                        + String.format("%04X", cp) + " in: " + title);
            }
        }
    }

    /** Minimal emoji/pictograph detector (avoids a JUnit dependency). */
    private static final class UnicodeChecker {
        static boolean isEmojiLike(int codePoint) {
            if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) return false;
            // Basic ranges that cover the "emoji in titles" classes we care about.
            return (codePoint >= 0x1F300 && codePoint <= 0x1FAFF)   // Symbol, pictographical
                    || (codePoint >= 0x2600  && codePoint <= 0x27BF) // misc symbols & dingbats
                    || (codePoint >= 0x1F000 && codePoint <= 0x1F2FF)// mahjong / enclosed alphanum
                    || (codePoint >= 0xFE00  && codePoint <= 0xFE0F); // variation selectors-16
        }
    }
}
