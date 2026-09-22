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
        jdbcTemplate.update("""
                INSERT INTO compositions
                    (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES ('Parts Table UI', 'regresja US-7.1 panel głosow', null, null,
                        'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = 'Parts Table UI'",
                Long.class);

        // One uploaded score file (metadata row; binary not needed by the table render path).
        jdbcTemplate.update("""
                INSERT INTO score_files
                    (composition_id, mime_type, size_bytes, sha256, storage_path, original_name, created_at)
                VALUES (%s, 'application/pdf', 1431911,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                        'polonez/test-polonez-glosy.pdf', 'Polonez (glosy).pdf', CURRENT_TIMESTAMP)
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

        // ── Bug 2: "＋ Dodaj głos" exists AFTER a saved row — it can be repeated, not one-shot. ─
        WebElement addBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("open-add-part-modal-btn")));
        assertThat(addBtn.getText()).as("add-part button label").contains("Dodaj głos");

        // The add-part <dialog> itself is present in the DOM — before the fix, the truncated
        // response never even delivered this element (it sits after the parts table).
        boolean dialogPresent = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                "return !!document.getElementById('add-part-dialog');"));
        assertThat(dialogPresent).as("#add-part-dialog is on the page").isTrue();

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
                "var m = document.getElementById('menu-modal');" +
                "if (m && m.close) { m.close(); }");
    }
}
