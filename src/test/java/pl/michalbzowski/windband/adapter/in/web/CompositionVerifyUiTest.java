package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.16 — the verification gate in the UI: "Zweryfikuj głosy — oznacz jako Gotowy".
 *
 * <p>Before this story the DRAFT → READY edge existed only in the service layer
 * ({@code verifyCompositionParts}, US-3.03) and in tests — an operator could never
 * legally bring a composition to READY from the app, which quietly made the whole
 * manual distribution path (US-6.4) unreachable for real data. These tests pin the
 * closed loop end-to-end:</p>
 * <ol>
 *   <li>a DRAFT composition WITH parts shows the button; confirming it stamps every
 *       part with the logged-in user's audit pair and flips the badge to "Gotowy";</li>
 *   <li>the button never reappears on a READY piece (note instead), and</li>
 *   <li>an EMPTY composition shows no button AND the endpoint refuses vacuous
 *       promotion — the DB stays DRAFT (the service itself would promote vacuously;
 *       the controller guard is what protects "READY ⇒ all voices verified").</li>
 * </ol>
 */
class CompositionVerifyUiTest extends UiTestBase {

    private static final Duration WAIT = Duration.ofSeconds(15);

    private String title;
    private Long compositionId;

    @BeforeEach
    void seedUniqueComposition() {
        // UUID-suffixed title: H2 shared TRUNCATE can silently no-op between tests in one JVM,
        // so lookups must never see "expected 1, actual N".
        title = "WERYFIKACJA " + java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO compositions (title, description, composer, arranger, status, band_id, created_at, updated_at) "
                        + "VALUES (?, 'sciezka US-7.16', null, null, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                title);
        compositionId = jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?", Long.class, title);
    }

    @Test
    void verifyButton_promotesDraftToReady_andStampsAuditPairOnEveryPart() {
        Long trumpetId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE band_id = 1 AND name = 'Trąbka'", Long.class);
        seedPart(trumpetId, "Trąbka 1");
        seedPart(trumpetId, "Trąbka 2");

        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        var wait = new WebDriverWait(driver, WAIT);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("parts-panel")));

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("verify-parts-btn")));
        driver.findElement(By.id("verify-parts-btn")).click();

        // Confirmation dialog (shared lifecycle modal), then submit.
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lifecycle-confirm-btn")));
        assertThat(driver.findElement(By.id("lifecycle-title")).getText())
                .contains("Zweryfikować głosy");
        driver.findElement(By.id("lifecycle-confirm-btn")).click();

        // Post/redirect/back: success banner + the badge flipped to "Gotowy".
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("verify-parts-result")));
        assertThat(driver.findElement(By.id("verify-parts-result")).getText())
                .contains("Zweryfikowano 2 głosów");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#compositions-content .badge"), "Gotowy"));

        // Audit stamp on EVERY part row — the US-3.03 contract, via the real logged-in identity.
        Long readyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE id = ? AND status = 'READY'", Long.class, compositionId);
        assertThat(readyCount).isEqualTo(1L);
        Long stamped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments "
                        + "WHERE composition_id = ? AND verified_by = 'admin@test.com' AND verified_at IS NOT NULL",
                Long.class, compositionId);
        assertThat(stamped).isEqualTo(2L);

        // READY shows the note, never the button again.
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("verify-ready-note")));
        assertThat(driver.findElements(By.id("verify-parts-btn"))).isEmpty();
    }

    @Test
    void emptyComposition_hasNoButton_andEndpointRefusesVacuousPromotion() {
        loginAndNavigateTo("/bands/1/compositions/" + compositionId);
        var wait = new WebDriverWait(driver, WAIT);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("parts-panel")));

        // No parts → no gate affordance at all.
        assertThat(driver.findElements(By.id("verify-parts-btn"))).isEmpty();

        // Even a hand-crafted POST must fail closed: flash-guard, redirect back (POST→GET→200),
        // and the composition stays DRAFT — never a vacuous "READY with zero voices".
        int status = xhrPost("/bands/1/compositions/" + compositionId + "/verify");
        assertThat(status).isEqualTo(200);
        Long stillDraft = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE id = ? AND status = 'DRAFT'", Long.class, compositionId);
        assertThat(stillDraft).isEqualTo(1L);
        Long stamped = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM composition_instruments WHERE composition_id = ? AND verified_at IS NOT NULL",
                Long.class, compositionId);
        assertThat(stamped).isZero();
    }

    // ---- helpers ----

    private void seedPart(Long instrumentId, String role) {
        jdbcTemplate.update(
                "INSERT INTO composition_instruments (composition_id, instrument_id, instrument_role,"
                        + " page_from, page_to, source, confidence_score, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 1, 4, 'MANUAL', 1.0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                compositionId, instrumentId, role);
    }

    /** Same-origin form POST through the browser session; returns the final (redirect-followed) status. */
    private int xhrPost(String path) {
        Object status = ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();"
                        + "xhr.open('POST', arguments[0], false);"
                        + "xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');"
                        + "xhr.send('');"
                        + "return xhr.status;",
                path);
        return ((Number) status).intValue();
    }
}
