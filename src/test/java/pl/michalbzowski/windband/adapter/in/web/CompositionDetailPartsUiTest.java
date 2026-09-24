package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test — the three reported "Szczegóły utworu" bugs (2026-09-22) shared ONE root
 * cause and MUST stay guarded as one scenario:
 *
 * <ol>
 *   <li>Hamburger menu did not open on this page while working everywhere else.</li>
 *   <li>After adding one part row there was no "＋ Dodaj głos" button to add more.</li>
 *   <li>The parts table rendered only Instrument + Rola — the Plik nut / Strona od / Strona do
 *       cells and the 📤 Udostępnij action were missing from the row.</li>
 * </ol>
 *
 * <p><b>Root cause (verified in production logs, Railway 2026-09-22T17:56Z):</b>
 * {@code partsFor()} returned raw JPA {@code CompositionInstrument} entities while the template
 * evaluated {@code p.scoreFileName} / {@code p.instrument.name}. Thymeleaf threw
 * {@code SpelEvaluationException EL1008E} mid-row, but the HTTP response had already been
 * committed — the browser received a TRUNCATED page: everything after the failing cell in the
 * parts table was absent, including the "Dodaj głos" button and the layout {@code footer-scripts}
 * that defines {@code openAppModal()} (hamburger). Any other composition without part rows never
 * evaluated the failing expression, which is why only this page was affected.</p>
 *
 * <p><b>The fix</b> (see {@code ScoreFileListQueryService#partsFor}) projects the parts into a
 * flat {@code CompositionInstrumentDto} inside the read transaction — every cell of the row plus
 * the share action render, the response is complete and the footer scripts load.</p>
 */
class CompositionDetailPartsUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long compositionId;

    @BeforeEach
    void seedCompositionWithOnePartAndOneScoreFile() throws Exception {
        // cleanDatabase() (UiTestBase) already truncated compositions / score_files /
        // composition_instruments. Re-insert a complete band-1 row set.
        // NOTE: use a UNIQUE title per test instance — H2's shared TRUNCATE ... CASCADE has been
        // observed to silently no-op on some of these tables, so earlier tests' rows can still be
        // present; a UUID title keeps "expected 1, actual N" lookup collisions impossible.
        String title = "Parts Table UI-" + java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja US-7.1 panel głosow', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?",
                Long.class, title);

        // One uploaded score file — metadata row PLUS a REAL on-disk 5-page PDF, so the
        // /thumb preview endpoint returns genuine JPEGs (the modal's header crop renders).
        Path scoresRoot = Files.createDirectories(
                Path.of(System.getProperty("java.io.tmpdir"), "windband-ui-scores"));
        Path pdfPath = scoresRoot.resolve(UUID.randomUUID() + "_Polonez_UI-glosy.pdf");
        byte[] pdf = build5PagePdfWithHeader();
        Files.write(pdfPath, pdf);

        jdbcTemplate.update("""
                INSERT INTO score_files
                    (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at)
                VALUES (%s, 'application/pdf', %d,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        '%s', 'Polonez (glosy).pdf', 5, CURRENT_TIMESTAMP)
                """.formatted(String.valueOf(compositionId), pdf.length, pdfPath.toString()));
        Long fileId = jdbcTemplate.queryForObject(
                "SELECT id FROM score_files WHERE original_name = 'Polonez (glosy).pdf' " +
                        "AND composition_id = ?", Long.class, compositionId);

        // Trąbka — the band-1 seed instrument (data.sql order: Trąbka, Bęben, Saksofon).
        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);

        // The part row the user reported: Dyrygent-style mapping with role + page range.
        jdbcTemplate.update(
                "INSERT INTO composition_instruments (composition_id, instrument_id, instrument_role," +
                        " page_from, page_to, score_file_id, source, confidence_score, created_at, updated_at) VALUES " +
                        "(?, ?, 'Partytura', 1, 5, ?, 'MANUAL', 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, instrumentId, fileId);
    }

    /**
     * A realistic 5-page A4 "score": each page carries a bold header line in the TOP-LEFT
     * corner (the exact strip the new modal preview crops out) and a page marker in the body.
     */
    private static byte[] build5PagePdfWithHeader() throws Exception {
        try (PDDocument doc = new PDDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            for (int i = 1; i <= 5; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                             new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA_BOLD), 28);
                    cs.newLineAtOffset(72, 760);   // A4 top-left corner — instrument name
                    cs.showText("Flet / strona " + i);
                    cs.endText();

                    cs.beginText();
                    cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                            org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 300);
                    cs.showText("body page " + i);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void detailPage_rendersFullPartsRow_addButtonAndWorkingHamburger() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        // The page shell (header fragment) — present on every layout page.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".menu-btn")));

        // ── Bug 3: the row must render ALL FIVE cells with the real stored values. ───────────
        // (Mockup: „Strona od" + „Strona do" merged into one „Strony" range column, so the
        // table is now Instrument | Rola | Plik nut | Strony | Akcje — five columns.)
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#existing-parts tbody tr:not(.empty-row)")));

        List<WebElement> rows = driver.findElements(By.cssSelector("#existing-parts tbody tr:not(.empty-row)"));
        assertThat(rows).as("at least one saved part row is rendered").isNotEmpty();

        WebElement row = rows.get(0);
        List<WebElement> cells = row.findElements(By.tagName("td"));
        assertThat(cells).as("the parts row has all five columns (Instrument…Akcje)").hasSize(5);

        assertThat(cells.get(0).getText()).as("Instrument cell").containsIgnoringCase("Trąbka");
        assertThat(cells.get(1).getText()).as("Rola cell").isEqualToIgnoringWhitespace("Partytura");
        assertThat(cells.get(2).getText()).as("Plik nut cell (the exact expression that crashed " +
                "EL1008E — 'scoreFileName' on the entity)")
                .isNotBlank();
        assertThat(cells.get(2).getText().contains("(dowolny plik)")).as("a bound score file shows " +
                "its name, not the '(dowolny plik)' fallback").isFalse();
        // page_range() DTO method: pageFrom=1, pageTo=5 → "1–5" (en-dash per the UI copybook).
        assertThat(cells.get(3).getText()).as("Strony cell (merged page range 1–5)")
                .isEqualToIgnoringWhitespace("1–5");

        // ── Bug 3b: the Akcje cell carries the 📤 Udostępnij button (it was cut off before). ──
        WebElement shareBtn = cells.get(4).findElement(By.cssSelector(".part-share-btn"));
        assertThat(shareBtn.getText()).as("Udostępnij action in the row").contains("Udostępnij");

        // ── US-7.11 regression: the share link must be the PUBLIC TOKENIZED URL — a 122-bit
        // random path segment that carries no band/composition/part id (iterable-URL leak was
        // the reported vulnerability) and resolves without login.
        shareBtn.click();
        wait.until(driver -> {
            Boolean open = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var d = document.getElementById('part-share-modal');" +
                    "return d && (d.open === true || d.hasAttribute('open'));"));
            return Boolean.TRUE.equals(open);
        });
        // The link is fetched asynchronously via GET .../token — wait for the input to fill.
        String shareLink = wait.until(driver -> {
            String v = driver.findElement(By.id("share-link-input")).getAttribute("value");
            return (v != null && !v.isEmpty()) ? v : null;
        });
        assertThat(shareLink).as("share link is /public/parts/<uuid>, leaking no ids")
                .matches(".*/public/parts/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(shareLink).doesNotContain("/bands/").doesNotContain("/compositions/");
        ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('part-share-modal'); if (d && d.close) { d.close(); }");

        // ── Bug 2: "＋ Dodaj głos" exists AFTER a saved row — it can be repeated, not one-shot. ─
        WebElement addBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("open-add-part-modal-btn")));
        assertThat(addBtn.getText()).as("add-part button label").contains("Dodaj głos");

        // The add-part <dialog> itself is present in the DOM — before the fix, the truncated
        // response never even delivered this element (it sits after the parts table).
        boolean dialogPresent = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return !!document.getElementById('add-part-dialog');"));
        assertThat(dialogPresent).as("#add-part-dialog is on the page").isTrue();

        // ── Regression (2026-09-23): clicking "＋ Dodaj głos" must actually OPEN the dialog.
        // The click binding was accidentally dropped in ac90648, so on mobile (and desktop)
        // the button looked inert — no dialog, no feedback.
        addBtn.click();
        Boolean addPartOpen = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('add-part-dialog');" +
                "if (!d) { return false; }" +
                "return d.open === true || d.hasAttribute('open');"));
        assertThat(addPartOpen).as("#add-part-dialog opens on Dodaj glos click").isTrue();
        ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('add-part-dialog'); if (d && d.close) { d.close(); }");

        //.onclick="openAppModal('menu-modal')" — openAppModal() is defined in the layout
        // footer-scripts; if those never arrived (truncated response) this click throws and the
        // modal stays closed. Asserting the OPEN attribute is what the user actually observed.
        driver.findElement(By.cssSelector(".menu-btn")).click();
        Boolean menuOpen = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                "var m = document.getElementById('menu-modal');" +
                "if (!m) { return false; }" +
                "return m.open === true || m.hasAttribute('open');"));
        assertThat(menuOpen).as("hamburger opens #menu-modal on the composition detail page")
                .isTrue();

        // Leave the UI clean: close the menu modal for any subsequent test in this browser.
        ((JavascriptExecutor) driver).executeScript(
                "var m = document.getElementById('menu-modal'); if (m && m.close) { m.close(); }");
    }

    /**
     * Regression — "Dodaj głos" modal (2026-09-24 request): when the user types a value into
     * "Strona od:" that is HIGHER than the current "Strona do:", "Strona do:" must be auto-bumped
     * to match. Two things were broken before:
     * <ol>
     *   <li>No auto-sync at all — the stale lower "do" value stayed and the submit guard
     *       ("Strona do musi być większa lub równa stronie od.") blocked saving.</li>
     *   <li>The field had {@code min="2"}, so even a correct single-page mapping like N–N failed
     *       native HTML validation on submit.</li>
     * </ol>
     */
    @Test
    void addPartModal_typingHigherPageFromBumpsPageToAndSaveSucceeds() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("open-add-part-modal-btn")));
        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(driver -> {
            Boolean open = (Boolean) ((JavascriptExecutor) driver).executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });

        // The second seeded instrument — keeps the saved row's (instrument, role) distinct from
        // the pre-existing "Trąbka / Partytura" seed row so row-count assertions stay deterministic.
        Long bekId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Bęben'", Long.class);

        JavascriptExecutor js = (JavascriptExecutor) driver;
        // Leave the optional file picker at its default (no binding → no page-count cross-check).
        js.executeScript("document.getElementById('part-score-file-picker').value = ''");
        // Pick Bęben among the band-1 instruments rendered by th:each="${bandInstruments}".
        js.executeScript(
                "var s = document.getElementById('part-instrument-picker'); s.value = String(arguments[0]);", bekId);
        driver.findElement(By.cssSelector("#add-part-form input[name='role']")).clear();
        driver.findElement(By.cssSelector("#add-part-form input[name='role']")).sendKeys("Bęben UI");

        // Reproduce the user scenario: default form has from=1, to=2. Type 4 into "Strona od" —
        // the auto-sync handler must bump "Strona do" from 2 straight to 4.
        // 2026-09-24 redesign: "Strona od/do" are hidden (display:none) so Selenium cannot
        // type into them — replicate the keystroke by setting the value + dispatching the
        // same 'input' event a real keystroke fires (the bump handler is event-driven).
        typeIntoHiddenPageField(js, "part-page-from", "4");
        wait.until(driver -> {
            String v = (String) ((JavascriptExecutor) driver).executeScript(
                    "return document.getElementById('part-page-to').value;");
            return "4".equals(v);
        });
        assertThat((String) js.executeScript("return document.getElementById('part-page-from').value;"))
                .as("Strona od keeps the typed value")
                .isEqualTo("4");

        // The error banner (rendered hidden by class="hidden") must not be visible after sync.
        boolean bannerHidden = (Boolean) js.executeScript(
                "var e = document.getElementById('part-range-error');" +
                "return e && e.classList.contains('hidden');");
        assertThat(bannerHidden).as("no range error after auto-bump").isTrue();

        // Save — before the fix this was blocked either by the stale to<from guard or by
        // min="2" native validation rejecting the 4–4 single-page range.
        driver.findElement(By.cssSelector("#add-part-form button[type='submit']")).click();

        // Wait for the round-trip: the row must land in the DB (real completion signal).
        wait.until(wd -> {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM composition_instruments ci " +
                            "JOIN instruments i ON i.id = ci.instrument_id " +
                            "WHERE ci.composition_id = ? AND i.name = 'Bęben'",
                    Integer.class, compositionId);
            return n != null && n > 0;
        });

        // Exactly ONE new part row for Bęben with the auto-synced range 4–4.
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments ci " +
                        "JOIN instruments i ON i.id = ci.instrument_id " +
                        "WHERE ci.composition_id = ? AND i.name = 'Bęben'",
                Integer.class, compositionId);
        assertThat(rows).as("one new part row for Bęben was saved").isEqualTo(1);

        Integer pageFrom = jdbcTemplate.queryForObject(
                "SELECT ci.page_from FROM composition_instruments ci " +
                        "JOIN instruments i ON i.id = ci.instrument_id " +
                        "WHERE ci.composition_id = ? AND i.name = 'Bęben'",
                Integer.class, compositionId);
        Integer pageTo = jdbcTemplate.queryForObject(
                "SELECT ci.page_to FROM composition_instruments ci " +
                        "JOIN instruments i ON i.id = ci.instrument_id " +
                        "WHERE ci.composition_id = ? AND i.name = 'Bęben'",
                Integer.class, compositionId);
        assertThat(pageFrom).as("page_from saved").isEqualTo(4);
        assertThat(pageTo).as("page_to auto-synced to page_from").isEqualTo(4);
    }

    /**
     * Requirement 2–7 — the modal shows a live PREVIEW of the selected PDF: only the
     * TOP strip of the current page (the instrument name sits top-left/top-right), ‹/›
     * navigation, an unmissable "Strona N z M" badge, and the shown page is auto-written
     * into "Strona od". The seed supplies a REAL 5-page PDF whose top-left header is
     * "Flet / strona N" — exactly what must be visible in the crop.
     */
    @Test
    void addPartModal_previewShowsTopStrip_navigationFlowsIntoPageFrom() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Open the modal — page render already auto-selected the 5-page file and
        // primed the preview with page 1 (browser-cached JPEG).
        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(driver -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });

        // Requirement 2: the preview block exists below "Plik nut" and is visible.
        Boolean previewVisible = (Boolean) js.executeScript(
                "var p = document.getElementById('part-preview');" +
                "return !!(p && !p.hasAttribute('hidden'));");
        assertThat(previewVisible).as("PDF preview block is shown").isTrue();

        // The <img> is pointed at the thumb URL only after a confirmed fetch 200, so any
        // load failure surfaces as dataset.lastFail + an inline status line. Poll for the
        // decoded bitmap; on timeout rethrow with whatever diagnostics the page recorded —
        // raw TimeoutException hides the real cause (HTTP status / 409 gate / auth 401 …).
        long deadline = System.currentTimeMillis() + WAIT.toMillis();
        String lastFail = "";
        String srcObserved = "";
        Boolean loaded = Boolean.FALSE;
        while (System.currentTimeMillis() < deadline) {
            Object lf = js.executeScript("return document.getElementById('part-preview').dataset.lastFail || '';");
            if (lf != null && !"".equals(String.valueOf(lf))) lastFail = String.valueOf(lf);
            srcObserved = String.valueOf(js.executeScript(
                    "var i = document.getElementById('part-preview-img'); return i.getAttribute('src') || '';"));
            loaded = (Boolean) js.executeScript(
                    "var i = document.getElementById('part-preview-img');" +
                    "return !!(i && i.getAttribute('src') && i.naturalWidth > 0);");
            if (Boolean.TRUE.equals(loaded)) break;
        }
        String statusLine = (String) js.executeScript(
                "var s = document.getElementById('add-part-status'); return s ? s.textContent : '';");
        String formAction = (String) js.executeScript(
                "var f = document.getElementById('add-part-form'); return f ? String(f.action) : '(no form)';");
        assertThat(Boolean.TRUE.equals(loaded))
                .as("preview bitmap decoded (form.action=[%s], src=[%s], lastFail=[%s], status=[%s])",
                        formAction, srcObserved, lastFail, statusLine)
                .isTrue();

        String src = (String) js.executeScript(
                "return document.getElementById('part-preview-img').getAttribute('src');");
        assertThat(src).as("preview img points at the thumb endpoint")
                .contains("/files/").contains("/thumb?page=1");

        // Requirement 6: prominent page badge "Strona 1 z 5".
        String badge = (String) js.executeScript(
                "return document.getElementById('part-preview-badge').textContent;");
        assertThat(badge).as("page badge").isEqualTo("Strona 1 z 5");

        // Requirement 7: the shown page drove "Strona od" automatically.
        assertThat((String) js.executeScript(
                "return document.getElementById('part-page-from').value;")).isEqualTo("1");

        // Requirement 5: ‹ disabled on page 1, › enabled (total 5 known).
        Boolean prevDisabledAtOne = (Boolean) js.executeScript(
                "return document.getElementById('part-preview-prev').disabled;");
        assertThat(prevDisabledAtOne).as("'‹' is disabled on the first page").isTrue();

        // Navigate forward: badge, image and "Strona od" must all move to page 2.
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(driver ->
                "Strona 2 z 5".equals((String) js.executeScript(
                        "return document.getElementById('part-preview-badge').textContent;")));
        assertThat((String) js.executeScript(
                "return document.getElementById('part-page-from').value;"))
                .as("Requirement 7 — navigating to page 2 wrote '2' into Strona od")
                .isEqualTo("2");
        // img.src flips only AFTER the page-2 thumbnail download is confirmed (fetch-first),
        // so poll for it instead of asserting immediately.
        wait.until(driver -> {
            String srcNow = (String) js.executeScript(
                    "return document.getElementById('part-preview-img').getAttribute('src') || '';");
            return srcNow.contains("/thumb?page=2");
        });

        // Requirement 5b: mid-navigation — one more '›' lands on page 3 and the image
        // re-points (src flips only after that page's thumbnail download is confirmed).
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(driver ->
                "Strona 3 z 5".equals((String) js.executeScript(
                        "return document.getElementById('part-preview-badge').textContent;")));
        wait.until(driver -> {
            String srcP3 = (String) js.executeScript(
                    "return document.getElementById('part-preview-img').getAttribute('src') || '';");
            return srcP3.contains("/thumb?page=3");
        });

        // Requirement 5c: on the LAST page (5 of 5) '›' is disabled and '‹' enabled.
        driver.findElement(By.id("part-preview-next")).click();
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(driver ->
                "Strona 5 z 5".equals((String) js.executeScript(
                        "return document.getElementById('part-preview-badge').textContent;")));
        Boolean nextDisabledAtLast = (Boolean) js.executeScript(
                "return document.getElementById('part-preview-next').disabled;");
        Boolean prevEnabledAtLast = !(Boolean) js.executeScript(
                "return document.getElementById('part-preview-prev').disabled;");
        assertThat(nextDisabledAtLast).as("'›' is disabled on the last page").isTrue();
        assertThat(prevEnabledAtLast).as("'‹' stays enabled until the first page").isTrue();
    }

    /**
     * User request (2026-09-24) — the page badge used to be pinned TOP-RIGHT of the
     * preview strip, exactly over the instrument name on score sheets, and the ‹/›
     * overlays blocked a clean look at the music. Two fixes guarded here:
     *
     * <ol>
     *   <li>The "Strona N z M" badge is centred in the middle of the preview —
     *       geometrically clear of both top corners where the instrument name sits.</li>
     *   <li>ONE click/tap anywhere on the preview (or Space/Enter when the box is
     *       focused) puts it into CALM mode: badge + arrows fade out completely AND
     *       stop capturing touches (bare sheet music, no dead zones). Clicking or
     *       tapping ANYWHERE again — even exactly where an arrow used to sit — brings
     *       them back. Enabled arrows keep navigating instead of toggling; a disabled
     *       arrow must not be a dead zone either.</li>
     * </ol>
     */
    @Test
    void addPartModal_previewBadgeCentredAndClickTogglesOverlayVisibility() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Open the modal; the page render pre-selected the 5-page seed file and primed
        // the page-1 bitmap.
        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(wd -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });
        wait.until(wd -> {
            Boolean loaded = (Boolean) js.executeScript(
                    "var i = document.getElementById('part-preview-img');" +
                    "return !!(i && i.getAttribute('src') && i.naturalWidth > 0);");
            return Boolean.TRUE.equals(loaded);
        });

        // ── 1) Badge is centred in the strip — clear of the top corners ──────────────
        Map<String, Object> geo = (Map<String, Object>) js.executeScript(
                "var b = document.getElementById('part-preview-badge').getBoundingClientRect();" +
                "var bp = document.getElementById('part-preview').getBoundingClientRect();" +
                "return {" +
                "  cx: b.left + b.width / 2, cy: b.top + b.height / 2," +
                "  pcx: bp.left + bp.width / 2, pcy: bp.top + bp.height / 2," +
                "  topOffset: b.top - bp.top, bHeight: b.height" +
                "};");
        double cx = ((Number) geo.get("cx")).doubleValue();
        double cy = ((Number) geo.get("cy")).doubleValue();
        double pcx = ((Number) geo.get("pcx")).doubleValue();
        double pcy = ((Number) geo.get("pcy")).doubleValue();
        double topOffset = ((Number) geo.get("topOffset")).doubleValue();
        double bHeight = ((Number) geo.get("bHeight")).doubleValue();

        assertThat(Math.abs(cx - pcx))
                .as("badge is horizontally centred (still clear of the instrument name)")
                .isLessThan(6.0);
        // 2026-09-24 redesign: the badge moved OUT of the middle of the strip into the
        // preview's BOTTOM BAR — assert it sits inside the bar near the bottom edge.
        Map<String, Object> barGeo = (Map<String, Object>) js.executeScript(
                "var br = document.getElementById('part-preview-bar').getBoundingClientRect();" +
                "var bp = document.getElementById('part-preview').getBoundingClientRect();" +
                "return {barTop: br.top - bp.top, barBottom: br.bottom - bp.top, boxH: bp.height};");
        double barTop = ((Number) barGeo.get("barTop")).doubleValue();
        double barBottom = ((Number) barGeo.get("barBottom")).doubleValue();
        // cy is viewport-absolute — convert to box-relative before comparing with the bar.
        assertThat(cy - pcy + ((Number) barGeo.get("boxH")).doubleValue() / 2.0)
                .as("badge is vertically inside the bottom bar (was floating mid-strip)")
                .isBetween(barTop - 2.0, barBottom + 2.0);
        assertThat(barBottom - barTop)
                .as("the bottom bar is a compact toolbar, not a full-height overlay")
                .isLessThan(90.0);
        assertThat(topOffset)
                .as("badge sits at/below the middle of the strip")
                .isGreaterThan(bHeight / 2 - 8.0);

        // ── 2) One click anywhere on the preview → calm mode; a second one restores it
        assertThat(previewCalm(js)).as("overlays are visible before any click").isFalse();

        String hitBody = clickInsidePreviewAt(js, 0.5, 0.2);
        assertThat(hitBody).as("the tap lands inside the preview strip (img or the bar row — both bubble to the toggle)")
                .isIn("part-preview-img", "part-preview-bar");
        assertThat(previewCalm(js)).as("ONE click on the preview switches to calm mode").isTrue();
        for (String id : OVERLAY_IDS) {
            requireOverlayIdle(js, id);
        }

        // Tapping EXACTLY where a disabled '‹' used to sit must still reach the sheet
        // music (no dead zone) and toggle the overlays back on.
        String hitArrowSpot = clickInsidePreviewAt(js, 0.04, 0.5);
        assertThat(hitArrowSpot).as("disabled arrow is not a dead zone — tap reaches the strip/bar")
                .isIn("part-preview-img", "part-preview-bar");
        assertThat(previewCalm(js))
                .as("the second tap — even over a former arrow spot — restores overlays").isFalse();
        for (String id : OVERLAY_IDS) {
            requireOverlayVisible(js, id);
        }

        // ── 3) Enabled arrows keep navigating — they never toggle calm mode
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 2 z 5".equals(previewBadgeText(js)));
        assertThat(previewCalm(js)).as("'›' navigates (page 1→2) without toggling").isFalse();

        driver.findElement(By.id("part-preview-prev")).click();
        wait.until(wd -> "Strona 1 z 5".equals(previewBadgeText(js)));
        assertThat(previewCalm(js)).as("'‹' navigates (page 2→1) without toggling").isFalse();

        // ── 4) Keyboard parity: Space / Enter on the focused box toggle the overlays
        js.executeScript("document.getElementById('part-preview').focus();");
        driver.switchTo().activeElement().sendKeys(Keys.SPACE);
        assertThat(previewCalm(js)).as("Space on the focused preview enters calm mode").isTrue();
        requireOverlayIdle(js, "part-preview-badge");

        driver.switchTo().activeElement().sendKeys(Keys.ENTER);
        assertThat(previewCalm(js)).as("Enter restores badge + arrows").isFalse();
        requireOverlayVisible(js, "part-preview-badge");
    }

    /**
     * User request (2026-09-24) — the preview BOTTOM BAR: arrows + badge live in a bar
     * pinned to the bottom edge of the preview component, next to two NEW same-size
     * buttons ⇤ / ⇥. "Strona od" and "Strona do" are hidden (not removed), so these two
     * buttons are the manual way to pin each end of the page range to the currently
     * previewed page. They must not toggle calm mode and must respect the bump rule.
     */
    @Test
    void addPartModal_bottomBar_buttonsSetHiddenPageRange() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(wd -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });
        wait.until(wd -> {
            Boolean loaded = (Boolean) js.executeScript(
                    "var i = document.getElementById('part-preview-img');" +
                    "return !!(i && i.getAttribute('src') && i.naturalWidth > 0);");
            return Boolean.TRUE.equals(loaded);
        });

        // ── 1) The bar is a bottom strip of the preview and carries ALL controls ────────
        Map<String, Object> geo = (Map<String, Object>) js.executeScript(
                "var bp = document.getElementById('part-preview').getBoundingClientRect();" +
                "var br = document.getElementById('part-preview-bar').getBoundingClientRect();" +
                "return {boxH: bp.height, gap: bp.bottom - br.bottom, barH: br.height};");
        double boxH = ((Number) geo.get("boxH")).doubleValue();
        double gap  = ((Number) geo.get("gap")).doubleValue();
        double barH = ((Number) geo.get("barH")).doubleValue();
        assertThat(gap).as("bar is pinned to the bottom edge of the preview").isBetween(-2.0, 2.0);
        // Compact toolbar row: ~42px buttons + padding. Asserted absolutely because
        // fitPreviewHeight may clamp the STRIP itself to its 84px min-height on small
        // viewports (headless default 800x600), where "fraction of box" is meaningless.
        assertThat(barH).as("bar is a compact toolbar row").isBetween(30.0, 90.0);
        for (String id : List.of("part-preview-prev", "part-preview-next",
                "part-preview-badge", "part-range-from", "part-range-to")) {
            Boolean inBar = (Boolean) js.executeScript(
                    "var c = document.getElementById(arguments[0]).getBoundingClientRect();" +
                    "var b = document.getElementById('part-preview-bar').getBoundingClientRect();" +
                    "return c.top >= b.top - 2 && c.bottom <= b.bottom + 2" +
                    "    && c.left >= b.left - 2 && c.right <= b.right + 2;", id);
            assertThat(inBar).as("%s sits inside the bottom bar", id).isTrue();
        }

        // ── 2) ⇤ / ⇥ are the SAME size as the arrows (user request: equal buttons) ──────
        Map<String, Object> sizes = (Map<String, Object>) js.executeScript(
                "var g = function(id){ var r = document.getElementById(id).getBoundingClientRect();" +
                "  return [Math.round(r.width), Math.round(r.height)]; };" +
                "return {arr: g('part-preview-prev'), from: g('part-range-from'), to: g('part-range-to')};");
        assertThat(((List<Number>) sizes.get("from")).get(0).intValue())
                .as("⇤ width equals ‹ width").isEqualTo(((List<Number>) sizes.get("arr")).get(0).intValue());
        assertThat(((List<Number>) sizes.get("from")).get(1).intValue())
                .as("⇤ height equals ‹ height").isEqualTo(((List<Number>) sizes.get("arr")).get(1).intValue());
        assertThat(((List<Number>) sizes.get("to")).get(0).intValue())
                .as("⇥ width equals ‹ width").isEqualTo(((List<Number>) sizes.get("arr")).get(0).intValue());

        // ── 3) Hidden fields: present in the DOM but not rendered ───────────────────────
        Boolean hiddenFields = (Boolean) js.executeScript(
                "var f = document.getElementById('part-page-from');" +
                "var t = document.getElementById('part-page-to');" +
                "return !!(f && t && f.closest('[hidden]') && t.closest('[hidden]')" +
                "    && !f.offsetParent && !t.offsetParent);");
        assertThat(hiddenFields).as("Strona od/do are hidden, NOT removed").isTrue();

        // ── 4) Navigate to page 3, then pin the range ends with ⇤ and ⇥ ─────────────────
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 2 z 5".equals(previewBadgeText(js)));
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 3 z 5".equals(previewBadgeText(js)));
        assertThat(previewCalm(js)).as("bar buttons never toggle calm mode").isFalse();

        js.executeScript("document.getElementById('part-range-from').click();");
        assertThat((String) js.executeScript("return document.getElementById('part-page-from').value;"))
                .as("⇤ pins hidden 'Strona od' to the previewed page 3").isEqualTo("3");
        assertThat((String) js.executeScript("return document.getElementById('part-page-to').value;"))
                .as("bump rule — 'Strona do' (default 2) rises to 3 with it").isEqualTo("3");

        // Move back to page 2: navigation itself re-pins the hidden "od" to 2 (req. 7 —
        // by design the previewed page drives 'od'), so ⇥ here sets a valid 2–2 range.
        driver.findElement(By.id("part-preview-prev")).click();
        wait.until(wd -> "Strona 2 z 5".equals(previewBadgeText(js)));
        js.executeScript("document.getElementById('part-range-to').click();");
        assertThat((String) js.executeScript("return document.getElementById('part-page-to').value;"))
                .as("⇥ pins 'do' to the previewed page 2").isEqualTo("2");

        // ⇤ on page 2 is a no-op re-pin; then jump to the last page and pin 'do' = 5.
        js.executeScript("document.getElementById('part-range-from').click();");
        assertThat((String) js.executeScript("return document.getElementById('part-page-from').value;"))
                .as("⇤ keeps 'od' at page 2").isEqualTo("2");
        driver.findElement(By.id("part-preview-next")).click();
        driver.findElement(By.id("part-preview-next")).click();
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 5 z 5".equals(previewBadgeText(js)));
        js.executeScript("document.getElementById('part-range-to').click();");
        assertThat((String) js.executeScript("return document.getElementById('part-page-to').value;"))
                .as("⇥ pins hidden 'Strona do' to the previewed last page").isEqualTo("5");

        // ── 5) Badge + arrows + range buttons all share the ONE bar row (no wrap) ──────
        Map<String, Object> rows = (Map<String, Object>) js.executeScript(
                "var g = function(id){ return document.getElementById(id).getBoundingClientRect(); };" +
                "var a = g('part-range-from'), n = g('part-preview-next');" +
                "return {fromTop: a.top, fromBottom: a.bottom, nextTop: n.top, nextBottom: n.bottom};");
        double fromTop = ((Number) rows.get("fromTop")).doubleValue();
        double nextTop = ((Number) rows.get("nextTop")).doubleValue();
        assertThat(Math.abs(fromTop - nextTop))
                .as("⇤ and › sit on the same bar row (no wrap)")
                .isLessThan(2.0);
    }

    /**
     * User request (2026-09-24, iteration 2) — the two range buttons no longer show
     * the ⇤ / ⇥ glyphs: each DISPLAYS the page number it holds, mirrored live from
     * the hidden "Strona od" / "Strona do" inputs. Rules under test:
     * <ul>
     *   <li>a) pressing the left (⇤) button on page 1 leaves it showing "1";</li>
     *   <li>b) pressing the right (⇥) button on page 1 makes it show "1";</li>
     *   <li>c) same on any other page — the pressed button shows that page number;</li>
     *   <li>d) the left digit never exceeds the right digit;</li>
     *   <li>e) the hidden fields remain the form's source of truth (still submitted);</li>
     *   <li>f) pressing the left button on a page HIGHER than the current right digit
     *       drags the right digit (and the hidden "do") up to the same number.</li>
     * </ul>
     */
    @Test
    void addPartModal_rangeButtonsDisplayTheirPageNumbers() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(wd -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });
        wait.until(wd -> {
            Boolean loaded = (Boolean) js.executeScript(
                    "var i = document.getElementById('part-preview-img');" +
                    "return !!(i && i.getAttribute('src') && i.naturalWidth > 0);");
            return Boolean.TRUE.equals(loaded);
        });

        // ── initial state: labels mirror the fields' defaults (od=1, do=2) ───────────
        assertThat(rangeButtonLabel(js, "part-range-from")).as("left button starts at 1").isEqualTo("1");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("right button starts at 2").isEqualTo("2");

        // ── a) page 1, press LEFT → it shows 1; the right keeps its own number ──────
        js.executeScript("document.getElementById('part-range-from').click();");
        assertThat(rangeButtonLabel(js, "part-range-from")).as("(a) left shows 1 on page 1").isEqualTo("1");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("(d) right untouched by left press").isEqualTo("2");

        // ── b) page 1, press RIGHT → it shows 1 (pinned to the previewed page) ───────
        js.executeScript("document.getElementById('part-range-to').click();");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("(b) right shows 1 on page 1").isEqualTo("1");
        assertLeftNotAfterRight(js);

        // ── c) page 3: navigation drives 'od' → left label follows; pressing either
        //     button on the previewed page pins it to 3 ─────────────────────────────────
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 2 z 5".equals(previewBadgeText(js)));
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 3 z 5".equals(previewBadgeText(js)));
        assertThat(rangeButtonLabel(js, "part-range-from")).as("(c) left follows the previewed page 3").isEqualTo("3");
        js.executeScript("document.getElementById('part-range-from').click();");
        assertThat(rangeButtonLabel(js, "part-range-from")).as("(c) left press pins 3").isEqualTo("3");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("bump rule — right rose to 3 with it").isEqualTo("3");
        js.executeScript("document.getElementById('part-range-to').click();");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("(c) right press pins 3").isEqualTo("3");
        assertLeftNotAfterRight(js);

        // ── e) the hidden fields still carry the SAME numbers → unchanged POST payload ─
        assertThat((String) js.executeScript("return document.getElementById('part-page-from').value;"))
                .as("hidden 'Strona od' still mirrors the left button").isEqualTo("3");
        assertThat((String) js.executeScript("return document.getElementById('part-page-to').value;"))
                .as("hidden 'Strona do' still mirrors the right button").isEqualTo("3");

        // ── f) page 5 with a manually CLAMPED-DOWN range 2–3: press LEFT on page 5 ────
        // The digits must be ordered, so first jump to page 5 (od/do → 5/5), then type
        // a lower 2–3 range (req. 8 keeps 'do' from dragging down, typing is not nav),
        // and finally press the left button WITHOUT navigating: it re-pins 'od' to the
        // previewed page 5 > current right digit 3 → right must follow up to 5.
        driver.findElement(By.id("part-preview-next")).click();
        driver.findElement(By.id("part-preview-next")).click();
        wait.until(wd -> "Strona 5 z 5".equals(previewBadgeText(js)));
        typeIntoHiddenPageField(js, "part-page-from", "2");
        typeIntoHiddenPageField(js, "part-page-to", "3");
        assertThat(rangeButtonLabel(js, "part-range-from")).as("typed 2 echoed on the left button").isEqualTo("2");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("typed 3 echoed on the right button").isEqualTo("3");
        js.executeScript("document.getElementById('part-range-from').click();");
        assertThat(rangeButtonLabel(js, "part-range-from")).as("(f) left on page 5 shows 5").isEqualTo("5");
        assertThat(rangeButtonLabel(js, "part-range-to")).as("(f) right is dragged up to 5 too").isEqualTo("5");
        assertThat((String) js.executeScript("return document.getElementById('part-page-to').value;"))
                .as("(f) hidden 'do' followed the bump").isEqualTo("5");
        assertLeftNotAfterRight(js);

        // The aria-labels carry the meaning the glyphs used to (which end is which).
        assertThat((String) js.executeScript(
                "return document.getElementById('part-range-from').getAttribute('aria-label');"))
                .startsWith("Strona od: 5");
        assertThat((String) js.executeScript(
                "return document.getElementById('part-range-to').getAttribute('aria-label');"))
                .startsWith("Strona do: 5");
    }

    /** (d) the left range digit must never sit past the right one. */
    private static void assertLeftNotAfterRight(JavascriptExecutor js) {
        int l = Integer.parseInt(rangeButtonLabel(js, "part-range-from"));
        int r = Integer.parseInt(rangeButtonLabel(js, "part-range-to"));
        assertThat(l).as("left button digit (%s) never exceeds right (%s)", l, r).isLessThanOrEqualTo(r);
    }

    private static String rangeButtonLabel(JavascriptExecutor js, String id) {
        return ((String) js.executeScript(
                "return document.getElementById(arguments[0]).textContent;", id)).trim();
    }

    /**
     * Requirement 8 — the "Strona do" bump rule in both directions: typing a HIGHER
     * "Strona od" drags "Strona do" up (live validation error while to &lt; from);
     * typing a LOWER "Strona od" must NOT drag "Strona do" down (the user-extended
     * range survives) and clears the inline error.
     */
    @Test
    void addPartModal_pageToBumpsUpButNeverDown() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(driver -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });

        // Fresh defaults: from=1, to=2.
        // Type 5 into "Strona od" (click selects all → replaces): "Strona do" must bump 2 → 5.
        typeIntoHiddenPageField(js, "part-page-from", "5");
        wait.until(driver ->
                "5".equals((String) js.executeScript(
                        "return document.getElementById('part-page-to').value;")));

        // Now deliberately shrink "Strona do" below "Strona od": 3 < 5 → inline error visible.
        typeIntoHiddenPageField(js, "part-page-to", "3");
        Boolean errorShown = (Boolean) js.executeScript(
                "var e = document.getElementById('part-range-error');" +
                "return !!(e && !e.classList.contains('hidden') && e.textContent.length > 0);");
        assertThat(errorShown).as("to<from raises the inline range error").isTrue();

        // Pull "Strona od" back DOWN to 2: the extended "do"=3 must STAY (not drag down),
        // and the error clears because from <= do again.
        typeIntoHiddenPageField(js, "part-page-from", "2");
        wait.until(driver -> {
            boolean ok = "2".equals((String) js.executeScript(
                    "return document.getElementById('part-page-from').value;"))
                    && "3".equals((String) js.executeScript(
                    "return document.getElementById('part-page-to').value;"));
            if (!ok) return false;
            Boolean hidden = (Boolean) js.executeScript(
                    "return document.getElementById('part-range-error').classList.contains('hidden');");
            return ok && Boolean.TRUE.equals(hidden);
        });
        assertThat((String) js.executeScript(
                "return document.getElementById('part-page-to').value;"))
                .as("Requirement 8 — Strona do survives the Strona od decrease")
                .isEqualTo("3");
    }

    /**
     * Requirements 9 + 12 + layout — "Zapisz głos" saves WITHOUT closing the dialog
     * (the next instrument can follow immediately), "Zamknij" closes it, and clicking a
     * non-empty "Rola" select-all's its text so typing replaces it.
     */
    @Test
    void addPartModal_saveKeepsDialogOpen_closeCloses_roleAutoselects() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(driver -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });

        Long saksofonId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Saksofon'", Long.class);
        js.executeScript("document.getElementById('part-instrument-picker').value = String(arguments[0]);",
                saksofonId);
        // Leave the default file + default pages (1 / 2) — a valid mapping.

        // ── Requirement 12: clicking into a non-empty "Rola" selects ALL its text, ────
        // so typing immediately replaces it wholesale instead of appending mid-word.
        WebElement role = driver.findElement(By.id("part-role-input"));
        // Non-empty value, set WITHOUT focus so the click below is a genuine user action.
        js.executeScript("document.getElementById('part-role-input').value = 'Saksofon II';");
        if ((Boolean) js.executeScript(
                "return document.activeElement && document.activeElement.id === 'part-role-input';")) {
            js.executeScript("document.activeElement.blur();");
        }
        role.click();   // must select-all the existing text (requirement 12)
        Boolean selectionCoversWholeValue = (Boolean) js.executeScript(
                "var el = document.getElementById('part-role-input');" +
                "return el.selectionStart === 0 && el.selectionEnd === el.value.length && el.value.length > 0;");
        assertThat(selectionCoversWholeValue)
                .as("clicking the role field selects its entire current value").isTrue();

        // And typing over that selection replaces it in one stroke.
        role.sendKeys("Saksofon IIb");
        assertThat((String) js.executeScript("return document.getElementById('part-role-input').value;"))
                .as("typing immediately after the click replaces the old value (no manual delete)")
                .isEqualTo("Saksofon IIb");

        // ── Requirement 9: save succeeds and the dialog STAYS OPEN. ────────────────────
        driver.findElement(By.id("save-part-btn")).click();
        wait.until(wd -> {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM composition_instruments ci " +
                            "JOIN instruments i ON i.id = ci.instrument_id " +
                            "WHERE ci.composition_id = ? AND i.name = 'Saksofon'",
                    Integer.class, compositionId);
            return n != null && n > 0;
        });

        Boolean stillOpen = (Boolean) js.executeScript(
                "var d = document.getElementById('add-part-dialog');" +
                "return d && (d.open === true || d.hasAttribute('open'));");
        assertThat(stillOpen).as("'Zapisz głos' keeps the modal open for the next part").isTrue();

        // The just-saved row is already visible in the table (HTMX-free live swap) —
        // and the form's fields usable state persists: picker still has a value.
        String pickerValue = (String) js.executeScript(
                "return document.getElementById('part-score-file-picker').value;");
        assertThat(pickerValue).as("file picker keeps its selection after save").isNotBlank();

        // ── Requirement 9b: "Zamknij" closes the dialog. ───────────────────────────────
        WebElement closeBtn = driver.findElement(By.cssSelector(".add-part-close-btn"));
        assertThat(closeBtn.isDisplayed()).as("'Zamknij' button is rendered next to 'Zapisz głos'").isTrue();
        closeBtn.click();
        wait.until(driver -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return !Boolean.TRUE.equals(open);
        });
    }

    /**
     * Requirements 1, 13, 14 — modal LAYOUT: the title is a centred TOP row (not a
     * left-side caption), "Strona od" and "Strona do" share ONE line, and the dialog
     * box itself fits the viewport (the form body scrolls internally if ever needed).
     */
    @Test
    void addPartModal_layout_titleOnTop_pageFieldsOneLine_fitsViewport() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(driver -> {
            Boolean open = (Boolean) js.executeScript(
                    "var d = document.getElementById('add-part-dialog');" +
                    "return d && (d.open === true || d.hasAttribute('open'));");
            return Boolean.TRUE.equals(open);
        });

        // Requirement 1 — title row sits ABOVE the "Plik nut" field and is centred.
        double titleTop = ((Number) js.executeScript(
                "return document.getElementById('add-part-dialog-title').getBoundingClientRect().top;")).doubleValue();
        double fileTop = ((Number) js.executeScript(
                "return document.getElementById('part-score-file-picker').getBoundingClientRect().top;")).doubleValue();
        assertThat(titleTop).as("Requirement 1 — the title is on top of the modal (above Plik nut)")
                .isLessThan(fileTop);

        double titleCenter = ((Number) js.executeScript(
                "var r = document.getElementById('add-part-dialog-title').getBoundingClientRect();" +
                "return r.left + r.width / 2;")).doubleValue();
        double dialogCenter = ((Number) js.executeScript(
                "var r = document.getElementById('add-part-dialog').getBoundingClientRect();" +
                "return r.left + r.width / 2;")).doubleValue();
        assertThat(Math.abs(titleCenter - dialogCenter))
                .as("the title is horizontally centred across the modal")
                .isLessThan(60.0);

        // Requirement 13 — "Strona od" and "Strona do" live on the SAME line.
        double fromTop = ((Number) js.executeScript(
                "return document.getElementById('part-page-from').getBoundingClientRect().top;")).doubleValue();
        double toTop = ((Number) js.executeScript(
                "return document.getElementById('part-page-to').getBoundingClientRect().top;")).doubleValue();
        assertThat(Math.abs(fromTop - toTop))
                .as("Requirement 13 — Strona od and Strona do share one line")
                .isLessThan(2.0);

        // Requirement 14 — the dialog frame stays inside the viewport (height <= innerHeight).
        double dialogHeight = ((Number) js.executeScript(
                "return document.getElementById('add-part-dialog').getBoundingClientRect().height;")).doubleValue();
        double innerHeight = ((Number) js.executeScript("return window.innerHeight;")).doubleValue();
        assertThat(dialogHeight).as("Requirement 14 — modal fits one screen (height <= viewport)")
                .isLessThanOrEqualTo(innerHeight + 2.0);

        // The footer action pair ("Zapisz głos" + "Zamknij") is on ONE line.
        double saveTop = ((Number) js.executeScript(
                "return document.getElementById('save-part-btn').getBoundingClientRect().top;")).doubleValue();
        double closeTop = ((Number) js.executeScript(
                "return document.querySelector('.add-part-close-btn').getBoundingClientRect().top;")).doubleValue();
        assertThat(Math.abs(saveTop - closeTop))
                .as("Requirement 9 — Zapisz and Zamknij share one footer row")
                .isLessThan(2.0);

        // And the removed "Wyczyść" button is gone.
        Boolean clearGone = (Boolean) js.executeScript(
                "return !document.getElementById('clear-part-form-btn');");
        assertThat(clearGone).as("Requirement 10 — Wyczyść button no longer exists").isTrue();
    }
    // ── add-part preview calm-mode helpers (shared by the UI tests above/below) ──────

    private static final List<String> OVERLAY_IDS =
            List.of("part-preview-badge", "part-preview-prev", "part-preview-next",
                    "part-range-from", "part-range-to");

    /** Hidden (display:none) number inputs cannot receive Selenium keystrokes —
     *  set the value and fire the same bubbling 'input' event typing would trigger. */
    private static void typeIntoHiddenPageField(JavascriptExecutor js, String id, String value) {
        js.executeScript("var el = document.getElementById(arguments[0]);" +
                "el.value = arguments[1];" +
                "el.dispatchEvent(new Event('input', {bubbles: true}));", id, value);
    }

    private static Boolean previewCalm(JavascriptExecutor js) {
        return (Boolean) js.executeScript(
                "return document.getElementById('part-preview').classList.contains('ap-calm');");
    }

    private static String previewBadgeText(JavascriptExecutor js) {
        return (String) js.executeScript(
                "return document.getElementById('part-preview-badge').textContent;");
    }

    /**
     * Mirrors a finger tap at fractional coordinates inside #part-preview: dispatches a
     * BUBBLING 'click' on whatever top element sits at that point — the sheet-music
     * &lt;img&gt; unless an overlay is still capturing there — exactly as a real
     * mouse/touch hit-test would resolve. Returns the id of the element that received it.
     */
    private static String clickInsidePreviewAt(JavascriptExecutor js, double fracX, double fracY) {
        List<?> rect = (List<?>) js.executeScript(
                "var b = document.getElementById('part-preview').getBoundingClientRect();" +
                        "return [b.left, b.top, b.width, b.height];");
        double x = ((Number) rect.get(0)).doubleValue() + ((Number) rect.get(2)).doubleValue() * fracX;
        double y = ((Number) rect.get(1)).doubleValue() + ((Number) rect.get(3)).doubleValue() * fracY;
        return (String) js.executeScript(
                "var el = document.elementFromPoint(Math.round(arguments[0]), Math.round(arguments[1]));" +
                        "el.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true, view: window}));" +
                        "return el.id;", x, y);
    }

    /**
     * Waits for the ~140ms opacity CSS transition to settle and reads the final value.
     * (Browser JSON-ifies integral numbers as Longs — never cast straight to Double.)
     */
    private static double settledOpacity(JavascriptExecutor js, String id) {
        Number a = null;
        Number b = null;
        long deadline = System.currentTimeMillis() + 2_500;
        while (System.currentTimeMillis() < deadline) {
            Number now = (Number) js.executeScript(
                    "return parseFloat(getComputedStyle(document.getElementById(arguments[0])).opacity);", id);
            if (a != null && b != null && Math.abs(now.doubleValue() - a.doubleValue()) < 0.01) {
                break;
            }
            b = a;
            a = now;
            try {
                Thread.sleep(45);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return ((Number) js.executeScript(
                "return parseFloat(getComputedStyle(document.getElementById(arguments[0])).opacity);", id)).doubleValue();
    }

    /** In CALM mode every overlay must be fully transparent AND out of the hit path. */
    private static void requireOverlayIdle(JavascriptExecutor js, String id) {
        assertThat(settledOpacity(js, id))
                .as("%s — fully transparent in calm mode", id)
                .isLessThan(0.02);
        assertThat((String) js.executeScript(
                        "return getComputedStyle(document.getElementById(arguments[0])).pointerEvents;", id))
                .as("%s — captures no touches in calm mode", id)
                .isEqualTo("none");
    }

    /**
     * After restore: overlays must be "present" again — fully opaque when enabled, or at
     * least their intentional disabled-dimming (~0.35, pre-existing look). CALM mode is
     * opacity 0, so anything above the disabled floor proves the restore happened.
     */
    private static void requireOverlayVisible(JavascriptExecutor js, String id) {
        Boolean enabled = (Boolean) js.executeScript(
                "return !document.getElementById(arguments[0]).disabled;", id);
        final double floor = enabled ? 0.95 : 0.20;
        assertThat(settledOpacity(js, id))
                .as("%s — visible again after restore", id)
                .isGreaterThan(floor);
        if (enabled) {
            assertThat((String) js.executeScript(
                            "return getComputedStyle(document.getElementById(arguments[0])).pointerEvents;", id))
                    .as("%s — enabled overlay captures touches again", id)
                    .isNotEqualTo("none");
        }
    }


}
