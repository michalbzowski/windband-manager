package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

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
    void seedCompositionWithOnePartAndOneScoreFile() {
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

        // One uploaded score file (metadata row; binary not needed by the table render path).
        // page_count=5 so the part's 1–5 range resolves through the US-7.11 covering-file gate.
        jdbcTemplate.update("""
                INSERT INTO score_files
                    (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, page_count, created_at)
                VALUES (%s, 'application/pdf', 1431911,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'polonez/test-polonez-glosy.pdf', 'Polonez (glosy).pdf', 5, CURRENT_TIMESTAMP)
                """.formatted(String.valueOf(compositionId)));
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
        driver.findElement(By.id("part-page-from")).clear();
        driver.findElement(By.id("part-page-from")).sendKeys("4");
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
}
