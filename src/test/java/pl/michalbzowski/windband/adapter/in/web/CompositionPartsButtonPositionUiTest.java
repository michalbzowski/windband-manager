package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression — Issue #242 „Przenieś przycisk + Dodaj głos na górę strony" (2026-09-25):
 * the „＋ Dodaj głos" button lived BELOW the parts table (last element of #parts-panel),
 * so on any composition with rows the user had to scroll down the whole table to add
 * another part. It now sits DIRECTLY under the „Głosy w nutach" header — always at the
 * top of the section, before the table, in every state (empty and non-empty).
 *
 * <p>This class seeds a composition with NO parts: one assertion proves the button is
 * above the table in the empty state (with the empty row hint pointing „above"), the
 * second proves the same DOM order when a saved part row exists — the layout that used
 * to bury the button at the bottom. The seeded title is UUID-suffixed (shared H2 TRUNCATE
 * no-op hazard from the memory of this codebase) and no score file / PDF is needed, so
 * seeding stays minimal.</p>
 */
class CompositionPartsButtonPositionUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long emptyCompositionId;
    private Long populatedCompositionId;

    @BeforeEach
    void seedCompositionsWithAndWithoutParts() {
        // cleanDatabase() (UiTestBase) already truncated compositions / composition_instruments.
        String emptyTitle = "Btn pozycja PUSTY-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja issue 242 bez glosow', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                emptyTitle);
        emptyCompositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, emptyTitle);

        String fullTitle = "Btn pozycja PEŁNY-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja issue 242 z glosem', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                fullTitle);
        populatedCompositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, fullTitle);

        // One saved part row — the band-1 seed instrument 'Trąbka' (data.sql order), no score
        // file: the only thing the test cares about is the rendered <tr> in the parts table.
        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO composition_instruments (composition_id, instrument_id, instrument_role, " +
                        "page_from, page_to, source, confidence_score, created_at, updated_at) VALUES " +
                        "(?, ?, 'Partytura', 1, 3, 'MANUAL', 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                populatedCompositionId, instrumentId);
    }

    /**
     * Issue #242: with an EMPTY parts table the button must still be ABOVE the table —
     * directly under the „Głosy w nutach" header — and the empty-row hint must point the
     * user to the right place (it used to say „dodaj poniżej", which became a lie once the
     * button moved above).
     */
    @Test
    void addButtonIsAbovePartsTable_emptyStateHintpointsUp() {
        loginAndNavigateTo("/bands/1/compositions/" + emptyCompositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#parts-panel header h3")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("open-add-part-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#existing-parts .empty-row")));

        // The exact issue: after the „Głosy w nutach" header comes the button, and ONLY
        // then the parts table. Before the fix the table sat between the header and the
        // button, so this ordering assertion is exactly what was red.
        String order = ((String) ((JavascriptExecutor) driver).executeScript(
                "var h = document.querySelector('#parts-panel header h3');" +
                "var b = document.getElementById('open-add-part-modal-btn');" +
                "var t = document.getElementById('existing-parts');" +
                "if (!h || !b || !t) { return 'missing-:' + (h ? '' : 'h') + (b ? '' : 'b') + (t ? '' : 't'); }" +
                "return (b.compareDocumentPosition(t) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0 &&" +
                       "(t.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) === 0" +
                       "? 'button-above-table' : 'button-below-or-misplaced';"));
        assertThat(order).as("Dodaj głos button sits between the Głosy w nutach header and the parts table")
                .isEqualTo("button-above-table");

        // The empty-state hint must match the new placement: the button is now ABOVE, so
        // „dodaj poniżej" (add below) points at nothing.
        String emptyText = wait.until(driver -> {
            String t = driver.findElement(By.cssSelector("#existing-parts .empty-row")).getText();
            return (t != null && !t.isEmpty()) ? t : null;
        });
        assertThat(emptyText).as("empty-row hint points ABOVE where the button now is")
                .containsIgnoringCase("dodaj powyżej");
    }

    /**
     * Issue #242 in its reported scenario: a composition WITH saved part rows used to bury
     * the button below the (possibly long) table — the user had to scroll. Same ordering
     * contract must hold when real rows are rendered between header and table is now the
     * header → button → table order, and clicking it still opens the add-part dialog.
     */
    @Test
    void addButtonIsAbovePartsTable_withSavedPartRowAndStillOpensDialog() {
        loginAndNavigateTo("/bands/1/compositions/" + populatedCompositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);

        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#existing-parts tbody tr:not(.empty-row)")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.id("open-add-part-modal-btn")));

        String order = ((String) ((JavascriptExecutor) driver).executeScript(
                "var h = document.querySelector('#parts-panel header h3');" +
                "var b = document.getElementById('open-add-part-modal-btn');" +
                "var t = document.getElementById('existing-parts');" +
                "if (!h || !b || !t) { return 'missing-:' + (h ? '' : 'h') + (b ? '' : 'b') + (t ? '' : 't'); }" +
                "return (b.compareDocumentPosition(t) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0 &&" +
                       "(t.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) === 0" +
                       "? 'button-above-table' : 'button-below-or-misplaced';"));
        assertThat(order).as("saved rows do not push the button below the table again")
                .isEqualTo("button-above-table");

        // Moved, but still functional: clicking must open the native <dialog> as before.
        driver.findElement(By.id("open-add-part-modal-btn")).click();
        Boolean addPartOpen = ((Boolean) ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('add-part-dialog');" +
                "if (!d) { return false; }" +
                "return d.open === true || d.hasAttribute('open');"));
        assertThat(addPartOpen).as("the relocated Dodaj głos button still opens the add-part dialog")
                .isTrue();
        ((JavascriptExecutor) driver).executeScript(
                "var d = document.getElementById('add-part-dialog'); if (d && d.close) { d.close(); }");
    }
}
