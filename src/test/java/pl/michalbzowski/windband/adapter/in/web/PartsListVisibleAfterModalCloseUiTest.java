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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for #240 — "Dodałem serię głosów — po zamknięciu modala, nie ma ich na liście":
 *
 * <ol>
 *   <li>user opens the "Dodaj głos" modal ONCE,</li>
 *   <li>saves several voices (the modal STAYS OPEN between saves — requirement 9),</li>
 *   <li>closes the modal via "Zamknij",</li>
 *   <li>expects the saved voices to be present in "Głosy w nutach" WITHOUT a page refresh.</li>
 * </ol>
 *
 * <p>Before this test every save-flow test asserted only the DATABASE after each click
 * ({@code SELECT COUNT(*)}) — none verified that the visible table actually gained the new
 * rows, so the silent no-op path of {@code refreshPartsPanelAfterSave()} (empty {@code catch},
 * early return when the fetched fragment lacks the table) could ship and go unnoticed. This test
 * asserts on the LIVE DOM after closing, which is exactly what the user sees.</p>
 */
class PartsListVisibleAfterModalCloseUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private Long compositionId;

    @BeforeEach
    void seedCompositionWithoutParts() {
        // A band-1 composition with NO part rows: the list starts in the empty state.
        String title = "Parts After Close UI-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) " +
                        "VALUES (?, 'regresja #240 lista glosow', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?",
                Long.class, title);
    }

    private long partCountInDb() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments WHERE composition_id = ?",
                Integer.class, compositionId);
        return n == null ? 0 : n;
    }

    @SuppressWarnings("unchecked")
    private List<String> dataRowRolesInDom() {
        return (List<String>) ((JavascriptExecutor) driver).executeScript(
            "var rows = Array.prototype.slice.call(document.querySelectorAll('#existing-parts tbody tr[data-part-id]'));" +
            "return rows.map(function (r) { return r.getAttribute('data-role'); });");
    }

    private int dataRowCountInDom() {
        Object n = ((JavascriptExecutor) driver).executeScript(
                "return document.querySelectorAll('#existing-parts tbody tr[data-part-id]').length;");
        return ((Number) n).intValue();
    }

    private boolean dialogOpen(JavascriptExecutor js) {
        Boolean open = (Boolean) js.executeScript(
                "var d = document.getElementById('add-part-dialog');" +
                "return d && (d.open === true || d.hasAttribute('open'));");
        return Boolean.TRUE.equals(open);
    }

    @Test
    void savedPartsAppearInListAfterClosingTheModal_withoutPageRefresh() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);

        // ── precondition: the list is EMPTY before we add anything ─────────────
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#existing-parts")));
        assertThat(dataRowCountInDom()).as("start-from-empty precondition holds").isZero();

        // ── open the modal ONCE (requirement 9: it stays open across saves) ────
        driver.findElement(By.id("open-add-part-modal-btn")).click();
        wait.until(d -> dialogOpen(js));

        // ── save the two voices in sequence WITHOUT reopening the modal ─────────
        for (String role : java.util.Arrays.asList("Trąbka 1", "Trąbka 2")) {
            long expected = partCountInDb() + 1;

            js.executeScript(
                    "document.getElementById('part-instrument-picker').value = String(arguments[0]);",
                    instrumentId);
            WebElement roleField = driver.findElement(By.id("part-role-input"));
            roleField.clear();
            roleField.sendKeys(role);
            // Leave the default file + default pages (1 / 2) — a valid mapping.

            driver.findElement(By.id("save-part-btn")).click();
            wait.until(driver -> partCountInDb() == expected);

            // The modal must still be OPEN so the next voice can follow immediately (req 9).
            assertThat(dialogOpen(js)).as("dialog stays open after saving '" + role + "'").isTrue();
        }

        assertThat(partCountInDb()).as("both voices were persisted").isEqualTo(2);

        // ── close via "Zamknij" ────────────────────────────────────────────────
        WebElement closeBtn = driver.findElement(By.cssSelector(".add-part-close-btn"));
        assertThat(closeBtn.isDisplayed()).as("'Zamknij' button is rendered in the footer").isTrue();
        closeBtn.click();
        wait.until(d -> !dialogOpen(js));

        // ── #240 regression: WITHOUT any page refresh the saved voices must be
        //    visible in the parts list ───────────────────────────────────────────
        wait.until(driver -> dataRowCountInDom() == 2);
        assertThat(dataRowRolesInDom())
                .as("#240 — both saved voices are listed after closing the modal, no reload needed")
                .containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2");
    }
}
