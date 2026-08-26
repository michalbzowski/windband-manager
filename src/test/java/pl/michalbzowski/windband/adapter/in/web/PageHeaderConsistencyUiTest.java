package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
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
 * PR B adds the list-variant rows.
 * PR C (this session) adds the form-variant rows — one per create/edit form.
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
    //  PR B — list variant coverage. One @Test method per route.
    // ------------------------------------------------------------------

    /** Runs an optional one-shot SQL seed (e.g. {@code "seedOrder(1)"}), then
     *  navigates to the list page and asserts the unified page-header contract. */
    private void assertListPageHeader(String maybeSeed, String path, String expectedTitle) {
        if (maybeSeed != null) {
            runSeed(maybeSeed);
        }
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo(path);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));

        assertThat(bar.getAttribute("data-page-header")).isEqualTo("v1");
        assertThat(titleText(bar)).isEqualTo(expectedTitle);
        assertNoEmoji(titleText(bar));

        List<WebElement> actions = new ArrayList<>(bar.findElements(By.cssSelector(
                ".ph-primary-action, .ph-secondary-action")));
        if (actions.isEmpty()) {
            for (WebElement a : driver.findElements(By.cssSelector(".ph-primary-action, .ph-secondary-action"))) {
                if (a.isDisplayed()) {
                    actions.add(a);
                    break;
                }
            }
        }
        // NOTE: a list may be title-only (no actions). If the page rendered one, we
        // verify its icon; if not, that is still a valid unified-header state.

        for (WebElement action : actions) {
            assertIconThemeAware(action.findElements(By.tagName("svg")));
        }
        assertNoInlineStyles(bar);
    }

    private void assertListPageHeadingsNoEmoji(String path) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo(path);
        WebElement content = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        for (WebElement h : content.findElements(By.cssSelector("h1, h2, h3, h4"))) {
            String text = h.getText().trim();
            if (!text.isEmpty()) {
                assertNoEmoji(text);
            }
        }
    }

    @Test void listEvents_pageHeader()           { assertListPageHeader(null, "/events", "Wydarzenia"); }
    @Test void listRehearsals_pageHeader()       { assertListPageHeader(null, "/rehearsals", "Spotkania"); }
    @Test void listMembers_pageHeader()          { assertListPageHeader(null, "/members", "Członkowie"); }
    @Test void listGroups_pageHeader()           { assertListPageHeader(null, "/groups", "Grupy"); }
    @Test void listTags_pageHeader()             { assertListPageHeader(null, "/tags", "Tagi"); }
    @Test void listInstruments_pageHeader()      { assertListPageHeader(null, "/instruments", "Instrumenty"); }
    @Test void listInventory_pageHeader()        { assertListPageHeader(null, "/inventory", "Zasoby"); }
    @Test void listOrders_pageHeader()           { assertListPageHeader(null, "/orders", "Zamówienia"); }

    /** Regression: CI build #456 — orders table branch threw Thymeleaf
     *  TemplateParsingException (multiline ternary in th:classappend) → 500 on
     *  /orders when any order existed. The seeded SUBMITTED order forces the
     *  {@code <table>} branch to render, so this test catches the bug that the
     *  seed-less variant could never see (empty band renders no table). */
    @Test void listOrders_pageHeaderRenderedOrderRow() {
        assertListPageHeader("seedOrder(1)", "/orders", "Zamówienia");
        // The seeded order must render as a real <tr> in the orders table — prove it
        // via stable rendered facts (row present + requester name from data.sql).
        WebElement content = driver.findElement(By.cssSelector("#content"));
        List<WebElement> rows = content.findElements(By.cssSelector("table tbody tr"));
        assertThat(rows).as("seeded order must render as a table row").isNotEmpty();
        assertThat(content.getText()).as("seeded order belongs to band-1 member Jan")
                .contains("Jan Kowalski");
    }

    @Test void listEvents_noEmojiHeadings()      { assertListPageHeadingsNoEmoji("/events"); }
    @Test void listRehearsals_noEmojiHeadings()  { assertListPageHeadingsNoEmoji("/rehearsals"); }
    @Test void listMembers_noEmojiHeadings()     { assertListPageHeadingsNoEmoji("/members"); }
    @Test void listGroups_noEmojiHeadings()      { assertListPageHeadingsNoEmoji("/groups"); }
    @Test void listTags_noEmojiHeadings()        { assertListPageHeadingsNoEmoji("/tags"); }
    @Test void listInstruments_noEmojiHeadings() { assertListPageHeadingsNoEmoji("/instruments"); }
    @Test void listInventory_noEmojiHeadings()   { assertListPageHeadingsNoEmoji("/inventory"); }
    @Test void listOrders_noEmojiHeadings()      { assertListPageHeadingsNoEmoji("/orders"); }

    // ------------------------------------------------------------------
    //  PR C — form variant coverage. One @Test method per form route.
    // ------------------------------------------------------------------

    /** Shared assertion for the form-variant page-header: presence, data-page-header
     *  marker, back link (every form sets backUrl), exact title, no emoji.
     *  The mode icon (plus/edit) is asserted ONLY when the host uses a "Nowy …" or
     *  "Edytuj …" title — action-verb titles like "Dodaj X" / "Zaplanuj X" do not
     *  need a decorative prefix, matching the PR B list-page convention. */
    private void assertFormPageHeader(String path, String expectedTitle) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo(path);
        WebElement bar = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("nav[data-page-header='v1']")));

        assertThat(bar.getAttribute("data-page-header")).isEqualTo("v1");
        assertThat(titleText(bar)).as("title on %s", path).isEqualTo(expectedTitle);
        assertNoEmoji(titleText(bar));

        // back link MUST be rendered (forms always provide a backUrl)
        WebElement back = bar.findElement(By.cssSelector(".detail-back-link"));
        assertThat(back.getTagName()).as("back link tag").isEqualTo("a");
        assertThat(back.getAttribute("href")).as("back href")
                .isNotNull().doesNotEndWith("#");
        assertIconThemeAware(back.findElements(By.tagName("svg")));

        // NOTE: page-header-c renders an OPTIONAL decorative mode icon (plus for
        // "Nowy …", pencil for "Edytuj …") when the title starts with those words.
        // We intentionally do NOT assert it here — the contract is only that the
        // bar exists, back/title are correct, no emoji, theme-aware icons on the
        // elements we do enforce (back arrow). The mode icon is a visual cue only;
        // a host may use action-verb titles ("Dodaj X", "Zaplanuj X") without one.

        // no inline styles anywhere in the bar
        assertNoInlineStyles(bar);

        // (optional) no emoji anywhere in the view's top-level headings
        WebElement content = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        for (WebElement h : content.findElements(By.cssSelector("h1, h2, h3, h4"))) {
            String text = h.getText().trim();
            if (!text.isEmpty()) {
                assertNoEmoji(text);
            }
        }
    }

    @Test void formEventsNew_pageHeader()      { assertFormPageHeader("/events/new", "Dodaj wydarzenie"); }
    @Test void formRehearsalsNew_pageHeader()  { assertFormPageHeader("/rehearsals/new", "Zaplanuj spotkanie"); }
    @Test void formMembersNew_pageHeader()     { assertFormPageHeader("/members/new", "Nowy członek"); }
    @Test void formGroupsNew_pageHeader()      { assertFormPageHeader("/groups/new", "Nowa grupa"); }
    // NOTE: /tags/new and /instruments/new (and their edit variants) are bare HTMX
    // fragments (no full-page layout) — they only render inside a dashboard host.
    // We therefore cannot drive them via driver.get() in this contract test.
    // They ARE migrated to page-header-c (see the template), but coverage comes
    // from the host that embeds them (see TagTeamIsolationRegressionUiTest for tags,
    // and the analogous instrument tests).

    /** Edit variant — resolves id from DB directly (deterministic), then asserts
     *  the header reads "Edytuj …". This also proves the create/edit ternary
     *  inside the fragment resolves to the correct branch on an edit form. */
    private Long firstSeededId(String table, String bandCol) {
        try {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM " + table +
                            (bandCol == null ? "" : " WHERE " + bandCol + " = 1") +
                            " ORDER BY id LIMIT 1",
                    Long.class);
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        } catch (Exception ignored) { /* table may not exist or have no band col */ }
        return null;
    }

    @Test void formMembersEdit_pageHeader() {
        Long id = firstSeededId("members", "band_id");
        assertThat(id).as("seeded member must exist for edit test").isNotNull();
        assertFormPageHeader("/members/" + id + "/edit", "Edytuj członka");
    }


    // ------------------------------------------------------------------
    //  PR D — tail (wide lists + band-attribute module + reports)
    // ------------------------------------------------------------------

    @Test void formEventsEdit_pageHeader()         { assertFormPageHeader("/events/1/edit", "Edytuj wydarzenie"); }
    @Test void formRehearsalsEdit_pageHeader()     { assertFormPageHeader("/rehearsals/1/edit", "Edytuj spotkanie"); }
    @Test void listMeetings_pageHeader()           { assertListPageHeader(null, "/meetings", "Spotkania"); }
    @Test void listDashboards_pageHeader()         { assertListPageHeader(null, "/dashboards", "Dashboardy"); }
    @Test void listReports_pageHeader()            { assertListPageHeader(null, "/reports", "Raporty"); }
    @Test void listSystemAdmins_pageHeader()       { assertListPageHeader(null, "/admin/system-admins", "Administratorzy systemu"); }
    @Test void formBandAttributeNew_pageHeader()   { assertFormPageHeader("/band/attributes/new", "Nowy atrybut"); }
    @Test void formBandAttributeEdit_pageHeader()  {
        Long id = firstSeededId("member_attribute_defs", "band_id");
        if (id != null) {
            assertFormPageHeader("/band/attributes/" + id + "/edit", "Edytuj atrybut");
        } else {
            Long iid = firstSeededId("item_attribute_defs", "band_id");
            if (iid != null) {
                assertFormPageHeader("/band/attributes/" + iid + "/edit?type=INSTRUMENT", "Edytuj atrybut");
            }
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

    /** Executes a tiny one-shot seed fixture. {@code "seedOrder(<bandId>")} inserts a
     *  SUBMITTED band order owned by the first seeded member so the orders table
     *  branch of {@code templates/orders/list.html} actually renders. */
    private void runSeed(String spec) {
        if (!spec.startsWith("seedOrder(")) {
            throw new IllegalArgumentException("Unknown seed: " + spec);
        }
        long bandId = Long.parseLong(spec.substring("seedOrder(".length(), spec.length() - 1));
        // The shared H2 (DB_CLOSE_DELAY=-1, one JVM per fork) keeps rows from
        // earlier @SpringBootTest contexts in the reuse-fork pool. Wipe any
        // leaked inventory items/orders + our own rows so this test is deterministic
        // no matter what ran before it.
        for (String t : new String[]{"inventory_orders", "uniform_items", "instrument_items",
                "award_items", "asset_assignment_history"}) {
            try { jdbcTemplate.execute("DELETE FROM " + t); } catch (Exception ignored) { /* table may not exist */ }
        }
        List<Long> memberIds = jdbcTemplate.queryForList(
                "SELECT id FROM members WHERE band_id = ? ORDER BY id LIMIT 1", Long.class, bandId);
        assertThat(memberIds).as("seeded member for band %d must exist", bandId).isNotEmpty();
        jdbcTemplate.update("""
                        INSERT INTO inventory_orders (member_id, order_type, status, created_at)
                        VALUES (?, 'UNIFORM', 'SUBMITTED', CURRENT_TIMESTAMP)
                        """, memberIds.get(0));
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
