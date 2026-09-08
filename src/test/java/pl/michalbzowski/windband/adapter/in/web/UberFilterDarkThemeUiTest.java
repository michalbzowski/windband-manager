package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import pl.michalbzowski.windband.UiTestBase;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Theme-aware verification for {@code #uber-filter-container} (t_6e2f0eef).
 *
 * <p>The container wraps the participant text filter and the response-status
 * pills ({@code .response-filter-btn.confirmed / .declined / .later /
 * .no-response}). Historical bug: app.css only defined LIGHT-theme palettes
 * for those pills — in {@code data-theme="dark"} (the app's explicit theme hook)
 * or under OS {@code prefers-color-scheme: dark} the pale pill backgrounds and
 * dark-pigment text landed illegible on the near-black page background, and the
 * "Status:" label inherited a low-contrast text-muted color.
 *
 * <p>This test drives the REAL Chromium UI (ui-test profile, live app):
 * <ol>
 *  <li>builds one event with members in ALL four response states so every
 *      pill (✅/❌/⏳/❓) is present;
 *  <li>in LIGHT mode reads the computed {@code background-color} /
 *      {@code color} of every pill and the label — this is the "pixel-identical"
 *      baseline, stored for comparison;
 *  <li>flips to dark via {@code document.documentElement.dataset.theme = 'dark'}
 *      (the same hook PicoCSS's own theme toggle uses — pure CSS, no reload)
 *      and re-reads computed styles, asserting every value actually CHANGED
 *      (a missing dark override would fall back to the light value);
 *  <li>computes the WCAG relative-luminance contrast ratio of each dark pill's
 *      foreground over its composited background (pill tint over page background)
 *      and fails below 4.5:1;
 *  <li>asserts no JS exceptions were raised while flipping themes.
 * </ol>
 *
 * <p>This is a regression guard for CSS palette changes in
 * {@code src/main/resources/static/css/app.css} — if the dark palette there
 * changes, this test MUST be updated in lockstep.
 */
class UberFilterDarkThemeUiTest extends UiTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ────────────────────────────────── helpers ───────────────────────

    private void waitForPills(int minCount) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        wait.until(d -> d.findElements(By.cssSelector("#uber-filter-container .stat")).size() >= minCount);
    }

    @Test
    void uberFilterContainerMustBeThemeAwareInBothModes() throws InterruptedException {
        // ── Fixture setup — 1 event with members in 4 distinct response states ──────
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // DEFENSE (same as sibling tests): a legacy instrument row with null
        // sort_priority makes MemberQueryService's Collectors.toMap(NPE) 500 the
        // whole /members page before we could create members. Sanitize first.
        jdbcTemplate.update("UPDATE instruments SET sort_priority = 0 WHERE sort_priority IS NULL");

        // Authenticate + grab the CSRF cookie (root "/" = dashboard page with #content).
        loginAndNavigateTo("/");

        // Create event via API (synchronous XHR).
        Long eventId = createEventViaApi("ThemeFixture" + uid);
        assertThat(eventId).as("event created").isNotNull();

        // Create members directly in the DB (deterministic, no UI form race).
        Long mA = createMemberDirectly("ThFilterA" + uid);
        Long mB = createMemberDirectly("ThFilterB" + uid);
        Long mC = createMemberDirectly("ThFilterC" + uid);

        // Event: 4 response states — CONFIRMED, DECLINED, LATER, NO_RESPONSE.
        inviteMemberToEvent(eventId, mA);
        setEventResponse(eventId, mA, "CONFIRMED");
        inviteMemberToEvent(eventId, mB);
        setEventResponse(eventId, mB, "DECLINED");
        inviteMemberToEvent(eventId, mC);
        setEventResponse(eventId, mC, "LATER");
        // 4th member: NO_RESPONSE (no response set → default)
        Long mD = createMemberDirectly("ThFilterD" + uid);
        inviteMemberToEvent(eventId, mD);

        // ── 1. LIGHT baseline ──────────────────────────────────────────────────────────
        driver.get(baseUrl() + "/events/" + eventId);
        waitForPills(4);

        String lightConfirmedBg   = css("#uber-filter-container .stat.confirmed",   "background-color");
        String lightConfirmedFg   = css("#uber-filter-container .stat.confirmed",   "color");
        String lightDeclinedBg    = css("#uber-filter-container .stat.declined",    "background-color");
        String lightDeclinedFg    = css("#uber-filter-container .stat.declined",    "color");
        String lightLaterBg       = css("#uber-filter-container .stat.later",       "background-color");
        String lightLaterFg       = css("#uber-filter-container .stat.later",       "color");
        String lightNoRespBg      = css("#uber-filter-container .stat.no-response", "background-color");
        String lightNoRespFg      = css("#uber-filter-container .stat.no-response", "color");
        String lightLabelColor    = css("#uber-filter-container .response-filter-label", "color");

        for (String v : new String[]{lightConfirmedBg, lightDeclinedBg, lightLaterBg,
                lightNoRespBg, lightConfirmedFg, lightLabelColor}) {
            assertThat(v).as("light baseline value must be non-empty").isNotBlank();
        }

        // The ACTIVE pill's explicit .active light values must also be readable.
        clickPill(".stat.confirmed.response-filter-btn");
        String activeLightBg = css("#uber-filter-container .response-filter-btn.confirmed.active",
                "background-color");
        assertThat(activeLightBg).as("light ACTIVE confirmed bg must be non-empty").isNotBlank();
        clickAllActiveOff();

        // ── 2. Flip to dark (data-theme, the same hook PicoCSS's own toggle uses) ──────
        ((JavascriptExecutor) driver).executeScript(
                "document.documentElement.dataset.theme = 'dark';");

        // Give Chromium a moment to re-evaluate all [data-theme="dark"] rules
        // before we read computed styles. Reading getComputedStyle on the same
        // JS turn (same microtask flush) as the attribute change can return the
        // pre-flip cached value in some Chromium versions — a short wait + one
        // layout-forcing read makes the flip fully applied.
        Thread.sleep(120);
        ((JavascriptExecutor) driver).executeScript(
                "var x=document.querySelector('#uber-filter-container');"
              + " if (x) { void x.offsetWidth; void x.offsetHeight; }");

        // ── 3. Now read dark-mode values (Chromium has flushed style recalc) ─
        String darkConfirmedBg    = css("#uber-filter-container .stat.confirmed",   "background-color");
        String darkConfirmedFg    = css("#uber-filter-container .stat.confirmed",   "color");
        String darkDeclinedBg     = css("#uber-filter-container .stat.declined",    "background-color");
        String darkDeclinedFg     = css("#uber-filter-container .stat.declined",    "color");
        String darkLaterBg        = css("#uber-filter-container .stat.later",       "background-color");
        String darkLaterFg        = css("#uber-filter-container .stat.later",       "color");
        String darkNoRespBg       = css("#uber-filter-container .stat.no-response", "background-color");
        String darkNoRespFg       = css("#uber-filter-container .stat.no-response", "color");
        String darkLabelColor     = css("#uber-filter-container .response-filter-label", "color");

        // If the dark theme did NOT override any of these, getComputedStyle
        // returns the LIGHT value verbatim. Any equality = regression.
        assertThat(darkConfirmedBg).as("dark confirmed bg must differ from light")
                .isNotEqualTo(lightConfirmedBg)
                .describedAs("light=" + lightConfirmedBg);
        assertThat(darkDeclinedBg).isNotEqualTo(lightDeclinedBg);
        assertThat(darkLaterBg).isNotEqualTo(lightLaterBg);
        assertThat(darkNoRespBg).isNotEqualTo(lightNoRespBg);

        assertThat(darkConfirmedFg).as("dark confirmed fg must differ from light")
                .isNotEqualTo(lightConfirmedFg);
        assertThat(darkDeclinedFg).isNotEqualTo(lightDeclinedFg);
        assertThat(darkLaterFg).isNotEqualTo(lightLaterFg);
        assertThat(darkNoRespFg).isNotEqualTo(lightNoRespFg);

        assertThat(darkLabelColor)
                .as("dark 'Status:' label color must differ from light")
                .isNotEqualTo(lightLabelColor);

        // Active pill must ALSO have its dark override applied.
        clickPill(".stat.confirmed.response-filter-btn");
        String activeDarkBg = css("#uber-filter-container .response-filter-btn.confirmed.active",
                "background-color");
        assertThat(activeDarkBg).as("dark ACTIVE confirmed bg must differ from light active (#d4edda)")
                .isNotEqualTo(activeLightBg);
        clickAllActiveOff();

        // ── 3. WCAG AA contrast on the composited dark background ──────────────────────
        // `body`'s background-color is transparent; PicoCSS sets the effective page
        // color via --pico-background-color. Read it as the underlying surface the pill
        // blends against.
        String pageBg = (String) ((JavascriptExecutor) driver).executeScript(
                "var v = getComputedStyle(document.documentElement)" +
                ".getPropertyValue('--pico-background-color').trim();" +
                " if (!v || v === 'transparent') v = 'white';" +
                " document.body.style.backgroundColor = v;" +
                " return getComputedStyle(document.body).backgroundColor;");
        checkContrast("confirmed  ", darkConfirmedFg, darkConfirmedBg, pageBg);
        checkContrast("declined   ", darkDeclinedFg,  darkDeclinedBg,  pageBg);
        checkContrast("later      ", darkLaterFg,     darkLaterBg,     pageBg);
        checkContrast("no-response", darkNoRespFg,    darkNoRespBg,    pageBg);

        // ── 4. Theme flipping must be synchronous CSS-only (no JS throws) ──────────────
        ((JavascriptExecutor) driver).executeScript(
                "document.documentElement.dataset.theme = 'light';");
        List<String> consoleErrors = readConsoleSevere();
        assertThat(consoleErrors)
                .as("flipping data-theme between light↔dark must not throw JS")
                .isEmpty();

        System.out.printf("[UberFilterDarkTheme] LIGHT confirmed=%s/%s  DARK confirmed=%s/%s  pageBg=%s%n",
                lightConfirmedFg, lightConfirmedBg, darkConfirmedFg, darkConfirmedBg, pageBg);
    }

    // ────────────────────────────────── helpers ───────────────────────────────────────

    private String css(String selector, String prop) {
        WebElement el = driver.findElement(By.cssSelector(selector));
        Object v = ((JavascriptExecutor) driver).executeScript(
                "return getComputedStyle(arguments[0]).getPropertyValue('" + prop + "');", el);
        return v == null ? "" : v.toString().trim();
    }

    private void clickPill(String selector) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].click();", driver.findElement(By.cssSelector(selector)));
    }

    /** Deactivate every pill that currently has the .active class. */
    private void clickAllActiveOff() {
        List<WebElement> btns = driver.findElements(
                By.cssSelector("#uber-filter-container .stat.response-filter-btn.active"));
        for (WebElement b : btns) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", b);
        }
    }

    /** Create a band-1 event via the XHR API (synchronous) and return its id. */
    private Long createEventViaApi(String name) {
        String idStr = (String) ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open('POST', '/api/events', false);"
                        + "xhr.setRequestHeader('Content-Type', 'application/json');"
                        + "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));"
                        + "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);"
                        + "xhr.send(JSON.stringify({name: arguments[0], date: '" + LocalDate.now() + "',"
                        + " startTime: '18:00', endTime: '20:00', paymentType: 'FREE',"
                        + " eventType: 'CONCERT', bandId: 1}));"
                        + "var parsed = JSON.parse(xhr.responseText);"
                        + "return parsed && parsed.id !== undefined ? String(parsed.id) : null;", name);
        if (idStr == null) throw new IllegalStateException("createEventViaApi returned no id");
        return Long.valueOf(idStr);
    }

    /** Insert a band-1 member directly into the DB. */
    private Long createMemberDirectly(String firstName) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO members (last_name, first_name, joined_date, active, email_consent_given, band_id) VALUES (?, ?, CURRENT_DATE, TRUE, FALSE, 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "Dark");
            ps.setString(2, firstName);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) throw new IllegalStateException("createMemberDirectly: no generated id");
        return key.longValue();
    }

    private List<String> readConsoleSevere() {
        List<String> out = new ArrayList<>();
        try {
            List<LogEntry> logs = driver.manage().logs().get(LogType.BROWSER).getAll();
            if (logs == null) return out;
            for (LogEntry e : logs) {
                String lvl = String.valueOf(e.getLevel()).toUpperCase();
                if (!lvl.contains("SEVERE") && !lvl.contains("ERROR")) continue;
                String m = e.getMessage() == null ? "" : e.getMessage();
                if (m.contains("/favicon.ico")) continue;
                if (m.contains("windband-utils.js") && m.contains("addEventListener")) continue;
                out.add(m);
            }
        } catch (Exception ignored) { /* provider without console logging */ }
        return out;
    }

    /** WCAG contrast of fg over pill-bg composited over page-bg; throws under 4.5:1. */
    private void checkContrast(String label, String fgCss, String pillBgCss, String pageBgCss) {
        int[] fg = parseColor(fgCss);
        int[] tint = parseColor(pillBgCss);
        int[] page = parseColor(pageBgCss);
        double t = tint[3] / 255.0;
        int bgR = (int) Math.round(tint[0] * t + page[0] * (1 - t));
        int bgG = (int) Math.round(tint[1] * t + page[1] * (1 - t));
        int bgB = (int) Math.round(tint[2] * t + page[2] * (1 - t));
        double ratio = contrastRatio(fg, new int[]{bgR, bgG, bgB});
        if (ratio < 4.5) {
            throw new AssertionError(label + " fg=" + fgCss + " pillBg=" + pillBgCss + " pageBg=" + pageBgCss
                    + " → ratio " + String.format("%.2f", ratio) + " < WCAG AA 4.5:1");
        }
    }

    /** Parse "#rrggbb" or "rgb()/rgba()" into [r,g,b,alpha] (0-8bit each). */
    private static int[] parseColor(String c) {
        if (c == null || c.isBlank()) return new int[]{0, 0, 0, 255};
        c = c.trim();
        if (c.startsWith("#")) {
            if (c.length() == 7) {
                return new int[]{Integer.parseInt(c.substring(1, 3), 16),
                        Integer.parseInt(c.substring(3, 5), 16),
                        Integer.parseInt(c.substring(5, 7), 16), 255};
            }
            if (c.length() == 9) {
                return new int[]{Integer.parseInt(c.substring(1, 3), 16),
                        Integer.parseInt(c.substring(3, 5), 16),
                        Integer.parseInt(c.substring(5, 7), 16),
                        Integer.parseInt(c.substring(7, 9), 16)};
            }
        } else if (c.startsWith("rgb")) {
            String inner = c.substring(c.indexOf('(') + 1, c.indexOf(')'));
            String[] p = inner.split(",");
            int alpha = p.length >= 4 ? (int) Math.round(Double.parseDouble(p[3].trim()) * 255) : 255;
            int r = (int) Math.round(Double.parseDouble(p[0]));
            int g = (int) Math.round(Double.parseDouble(p[1]));
            int b = (int) Math.round(Double.parseDouble(p[2]));
            return new int[]{r, g, b, alpha};
        }
        return new int[]{0, 0, 0, 255};
    }

    private static double contrastRatio(int[] a, int[] b) {
        double la = relLum(a);
        double lb = relLum(b);
        if (la < lb) {
            double tmp = la;
            la = lb;
            lb = tmp;
        }
        return (la + 0.05) / (lb + 0.05);
    }

    private static double relLum(int[] c) {
        double[] s = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = c[i] / 255.0;
            s[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2];
    }
}
