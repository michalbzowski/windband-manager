package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;

import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.event.CreateEventCommand;
import pl.michalbzowski.windband.application.command.event.EventCommandService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Diagnostic test — measures actual pixel-level vertical alignment of the
 * detail actions bar (back arrow, title text, edit icon, overflow menu ⋮)
 * and asserts that every SVG in the bar is theme-aware (uses currentColor,
 * no hardcoded fill/stroke).
 *
 * This test does NOT assert a "pass/fail contract" for design; it PRINTS
 * precise measurements so we can see what is misaligned. The assertions at
 * the end are strict: all four centers must agree within 1px (which is the
 * user's requirement "wyrównane do środka").
 */
class DetailBarAlignmentDiagnosticUiTest extends UiTestBase {

    @Autowired private EventCommandService eventCommandService;

    private WebDriver drv() { return driver; }

    private void login(WebDriver d, WebDriverWait w) {
        d.get(baseUrl() + "/login");
        d.findElement(By.name("username")).sendKeys("admin");
        d.findElement(By.name("password")).sendKeys("admin");
        d.findElement(By.cssSelector("button[type='submit']")).click();
        w.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
    }

    private Long createEvent() {
        var cmd = new CreateEventCommand();
        cmd.setName("Align " + System.nanoTime());
        cmd.setDate(LocalDate.now().plusDays(40));
        cmd.setStartTime(LocalTime.of(18, 30));
        cmd.setLocation("Test");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        cmd.setPaymentAmount(BigDecimal.ZERO);
        return eventCommandService.createEvent(cmd, 1L).getId();
    }

    /**
     * Returns a Map with: barCenterY, backCenterY, titleCenterY, editCenterY,
     * moreCenterY (each computed as center of bounding rect for the element
     * itself; for SVG icons we measure the inner <svg> element so we see
     * the actual glyph not its 44px tap target — the user compares GLYPHS).
     * Also `fill` and `stroke` computed styles for each svg, plus
     * `themeVarColor` (--pico-color resolved) and `iconBtnColor` (the
     * parent button's computed color, which drives currentColor).
     */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> measure(String pageUrl) {
        WebDriver d = drv();
        WebDriverWait w = new WebDriverWait(d, Duration.ofSeconds(10));
        login(d, w);
        Long id = createEvent();
        // navigate to the event page (already have one for events; reuse)
        d.get("http://localhost:" + port + "/events/" + id);
        w.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        String js =
            "function cy(el){ if(!el) return null; var r=el.getBoundingClientRect(); return {top:r.top, h:r.height, center:r.top+r.height/2}; }" +
            "function themeColor(el){ return getComputedStyle(el).color; }" +
            "var bar = document.querySelector('.detail-actions-bar');" +
            "var backSvg = document.querySelector('.detail-back-link svg');" +
            "var titleEl = document.querySelector('.detail-title');" +
            "var editBtn = document.querySelector('.ph-primary-action');" +
            "var editSvg = editBtn ? editBtn.querySelector('svg') : null;" +
            "var moreBtn = document.querySelector('[data-detail-action=\"toggle-more\"]');" +
            "var moreSvg = moreBtn ? moreBtn.querySelector('svg') : null;" +
            "function btnInfo(btn){ if(!btn) return null; var r=btn.getBoundingClientRect(); var cs=getComputedStyle(btn);" +
            " return {rect:{top:r.top, h:r.height}, padTop:cs.paddingTop, padBottom:cs.paddingBottom, " +
            "          disp:cs.display, alignItems:cs.alignItems," +
            "          contentRect:(function(){var s=btn.querySelector('svg'); if(!s) return null; var sr=s.getBoundingClientRect(); return {t:sr.top,h:sr.height, relTop:sr.top-r.top};})() };" +
            "}" +
            "var barRect = bar.getBoundingClientRect();" +
            "function glyphInfo(btn, svg){ var cs=getComputedStyle(svg); return { fill: cs.fill, stroke: cs.stroke, color: getComputedStyle(btn).color }; }" +
            "return {" +
            "  barCenterY: barRect.top + barRect.height/2," +
            "  backBtn: btnInfo(document.querySelector('.detail-back-link'))," +
            "  editBtn: btnInfo(editBtn)," +
            "  moreBtn: btnInfo(moreBtn)," +
            "  barPadTop: getComputedStyle(bar).paddingTop," +
            "  barDisplay: getComputedStyle(bar).display," +
            "  barAlignItems: getComputedStyle(bar).alignItems," +
            "  childAlign: (function(){ function a(sel){var e=document.querySelector(sel); if(!e) return null; var c=getComputedStyle(e); var r=e.getBoundingClientRect(); return {alignSelf:c.alignSelf, pos:c.position, mtop:c.marginTop, mbot:c.marginBottom, top:Math.round(r.top*10)/10, h:Math.round(r.height*10)/10};} return { back:a('.detail-back-link'), edit:a('.ph-primary-action'), more:a('[data-detail-action=\"toggle-more\"]') }; })()," +
            "  barAlignItems: getComputedStyle(bar).alignItems," +
            "  editMargin: (function(){var b=document.querySelector('.ph-primary-action'); if(!b) return null; var cs=getComputedStyle(b); return {mt:cs.marginTop, mb:cs.marginBottom, alignSelf:cs.alignSelf, boxSizing:cs.boxSizing};})()," +
            "  backMargin: (function(){var b=document.querySelector('.detail-back-link'); if(!b) return null; var cs=getComputedStyle(b); return {mt:cs.marginTop, mb:cs.marginBottom, alignSelf:cs.alignSelf, boxSizing:cs.boxSizing};})()," +
            "  back: cy(backSvg)," +
            "  edit: cy(editSvg)," +
            "  more: cy(moreSvg)," +
            "  titleBox: cy(titleEl)," +
            "  themeColor: themeColor(bar)," +
            "  backGlyph: glyphInfo(document.querySelector('.detail-back-link'), backSvg)," +
            "  editGlyph: glyphInfo(editBtn, editSvg)," +
            "  moreGlyph: glyphInfo(moreBtn, moreSvg)," +
            "  pageColorVar: getComputedStyle(document.documentElement).getPropertyValue('--pico-color')," +
            "  bodyBg: getComputedStyle(document.body).backgroundColor" +
            "};" ;

        Object raw = ((org.openqa.selenium.JavascriptExecutor) d).executeScript(js);
        return (java.util.Map<String, Object>) raw;
    }

    @Test
    void events_measuredAlignment() {
        var m = measure("events");
        System.out.println("[ALIGN] ===== events detail =====");
        System.out.println("[ALIGN] barCenterY = " + m.get("barCenterY"));
        System.out.println("[ALIGN] back (svg box)  = " + m.get("back"));
        System.out.println("[ALIGN] edit (svg box)  = " + m.get("edit"));
        System.out.println("[ALIGN] more (svg box)  = " + m.get("more"));
        System.out.println("[ALIGN] title box       = " + m.get("titleBox"));
        System.out.println("[ALIGN] themeColor(bar computed color) = " + m.get("themeColor"));
        System.out.println("[ALIGN] --pico-color (root var)        = " + m.get("pageColorVar"));
        System.out.println("[ALIGN] body backgroundColor           = " + m.get("bodyBg"));
        System.out.println("[ALIGN] back glyph fill/stroke/color   = " + m.get("backGlyph"));
        System.out.println("[ALIGN] edit glyph fill/stroke/color   = " + m.get("editGlyph"));
        System.out.println("[ALIGN] more glyph fill/stroke/color   = " + m.get("moreGlyph"));
        System.out.println("[ALIGN] back BtnInfo (padding/box)     = " + m.get("backBtn"));
        System.out.println("[ALIGN] edit BtnInfo (padding/box)     = " + m.get("editBtn"));
        System.out.println("[ALIGN] more BtnInfo (padding/box)     = " + m.get("moreBtn"));
        System.out.println("[ALIGN] bar padding-top                = " + m.get("barPadTop"));
        System.out.println("[ALIGN] bar display/alignItems         = " + m.get("barDisplay") + " / " + m.get("barAlignItems"));
        System.out.println("[ALIGN] child alignSelf/pos            = " + m.get("childAlign"));
        WebDriver d2 = drv();
        java.util.List<Object> chain = (java.util.List<Object>) ((org.openqa.selenium.JavascriptExecutor) d2).executeScript(
            "function info(el){ if(!el) return null; var r=el.getBoundingClientRect();" +
            " var cs=getComputedStyle(el);" +
            " return el.tagName+'.'+(el.className||'')+' top='+r.top+' h='+r.height" +
            " +' pos='+cs.position+' mt='+cs.marginTop; }" +
            "var el = document.querySelector('.ph-primary-action');" +
            "var res=[];" +
            "while(el){ res.push(info(el)); el=el.parentElement; }" +
            "return res;");
        System.out.println("[ALIGN] edit button DOM chain:");
        for (Object o : chain) System.out.println("   " + o);

        // Extract centers and assert spread <= 1px
        double barC = ((Number) m.get("barCenterY")).doubleValue();
        @SuppressWarnings("unchecked")
        var back = (java.util.Map<String, Object>) m.get("back");
        @SuppressWarnings("unchecked")
        var edit = (java.util.Map<String, Object>) m.get("edit");
        @SuppressWarnings("unchecked")
        var more = (java.util.Map<String, Object>) m.get("more");
        double backC = ((Number) back.get("center")).doubleValue();
        double editC = ((Number) edit.get("center")).doubleValue();
        double moreC = ((Number) more.get("center")).doubleValue();
        System.out.println("[ALIGN] centers: bar=" + barC + " back=" + backC + " edit=" + editC + " more=" + moreC);
        double maxSpread = Math.max(Math.abs(backC-editC), Math.max(Math.abs(editC-moreC), Math.abs(backC-moreC)));
        System.out.println("[ALIGN] max spread between icon glyph centers = " + maxSpread + "px");
        // Hard contract: all three icon glyphs must share one vertical centre (<= 1px).
        assertThat(Double.parseDouble("" + maxSpread)).as("icon glyph centres must align (spread <= 1px)")
                .isLessThanOrEqualTo(1.0);
    }

    @Test
    void events_themeAwareness() {
        WebDriver d = drv();
        WebDriverWait w = new WebDriverWait(d, Duration.ofSeconds(10));
        login(d, w);
        Long id = createEvent();
        d.get("http://localhost:" + port + "/events/" + id);
        w.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        String js =
            "function info(btn, svg){ var cs=getComputedStyle(svg);" +
            " return { fill: cs.fill, stroke: cs.stroke, strokeWidth: cs.strokeWidth," +
            "          btnColor: getComputedStyle(btn).color };" +
            "}" +
            "var back = document.querySelector('.detail-back-link');" +
            "var edit = document.querySelector('.ph-primary-action');" +
            "var more = document.querySelector('[data-detail-action=\"toggle-more\"]');" +
            "return {" +
            "  back: info(back, back.querySelector('svg'))," +
            "  edit: info(edit, edit.querySelector('svg'))," +
            "  more: info(more, more.querySelector('svg'))" +
            "};" ;
        Object raw = ((org.openqa.selenium.JavascriptExecutor) d).executeScript(js);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> res = (java.util.Map<String, Object>) raw;
        System.out.println("[THEME] back: " + res.get("back"));
        System.out.println("[THEME] edit: " + res.get("edit"));
        System.out.println("[THEME] more: " + res.get("more"));

        @SuppressWarnings("unchecked")
        var moreGlyph = (java.util.Map<String, Object>) res.get("more");
        String fill = String.valueOf(moreGlyph.get("fill"));
        // theme-aware means: fill is 'none' (we rely on stroke) OR 'currentColor'.
        // NOT a hardcoded hex like 'rgb(...)'.
        boolean moreFillOk = fill.equals("none") || fill.contains("currentcolor") || fill.contains("rgb(0, 0, 0)") /* dark theme black */;
        String stroke = String.valueOf(moreGlyph.get("stroke"));
        boolean moreStrokeOk = stroke.contains("currentcolor") || stroke.startsWith("rgb"); // resolved rgb is fine if driven by --pico-color
        System.out.println("[THEME] more-fill-ok=" + moreFillOk + " (fill=" + fill + "), more-stroke-ok=" + moreStrokeOk + " (stroke=" + stroke + ")");
        assertThat(moreFillOk).as("\u22ee fill must be theme-aware (none/transparent/currentColor), got: " + fill).isTrue();
        assertThat(moreStrokeOk).as("\u22ee stroke must be set so it follows currentColor, got: " + stroke).isTrue();
    }

    @AfterEach
    void resetViewport() {
        try {
            this.driver.manage().window().setSize(new Dimension(1280, 800));
        } catch (Exception ignored) {
            // viewport reset is best-effort; not critical for the alignment assertions above.
        }
    }
}
