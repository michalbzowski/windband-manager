package pl.michalbzowski.windband.adapter.in.web;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-7.3 — Selenium UI tests for the band-scoped instrument→role admin page:
 * add a mapping (form POST), see it on the list, verify a duplicate is rejected
 * with the Polish message, and delete the row again. Uses the real admin login
 * (band 1) so the {@code belongsToTeam} guard runs through the actual security chain.
 */
class InstrumentRoleMapsAdminUiTest extends UiTestBase {

    private static final String PAGE = "/bands/1/instrument-roles";

    /** Tag guaranteed unique per test method — never a stable string another suite could leave behind. */
    private String tag() {
        return "UI-TEST-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void cleanUpPreviousRuns() {
        // instrument_role_map is not part of UiTestBase.cleanDatabase()'s truncate list (it is a
        // child of bands, which we keep); remove this test's rows explicitly — the same
        // DELETE-then-reseed pattern used there for member_groups.
        jdbcTemplate.execute("DELETE FROM instrument_role_map WHERE source_tag LIKE 'UI-TEST-%'");
    }

    @Test
    void addMapping_persists_visibleOnList_andCanBeDeleted() {
        String tag = tag();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        loginAndNavigateTo(PAGE);
        fillAndWait(wait, tag, "Klarnety 1", "dodane Seleniumem");
        submitForm();

        // Server renders success banner + new row right after the POST (no client redirect).
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("role-map-success")));
        var row = waitUntilTagVisible(tag);
        assertThat(row.getText()).contains("Klarnety 1");
        assertThat(row.getText()).contains("dodane Seleniumem");

        // DB-level persistence is the load-bearing assertion (the DOM could render stale state).
        pollCount("SELECT COUNT(*) FROM instrument_role_map WHERE source_tag = ? AND target_role_pattern = ?", 1,
                tag, "Klarnety 1");

        // Delete again → row disappears from the list and the table.
        var deleteForm = row.findElement(By.tagName("form"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].requestSubmit();", deleteForm);

        wait.until(ExpectedConditions.stalenessOf(row));
        pollCount("SELECT COUNT(*) FROM instrument_role_map WHERE source_tag = ?", 0, tag);
    }

    @Test
    void addingDuplicateShowsPolishError_andDoesNotPersist() {
        String tag = tag();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        loginAndNavigateTo(PAGE);

        // Seed one row directly: deterministic state, no double form interaction.
        // Both created_at/updated_at are NOT NULL in V39 — set both explicitly.
        jdbcTemplate.update("INSERT INTO instrument_role_map " +
                "(band_id, source_tag, target_role_pattern, created_at, updated_at) " +
                "VALUES (1, ?, 'Tuba 1', now(), now())", tag);

        fillAndWait(wait, tag, "Tuba 1", null);
        submitForm();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("role-map-form-errors")));
        assertThat(driver.findElement(By.id("role-map-form-errors")).getText())
                .contains("już istnieje w tym zespole");

        // Exactly one row exists now (the seeded one) — the duplicate attempt was rejected.
        pollCount("SELECT COUNT(*) FROM instrument_role_map WHERE source_tag = ?", 1, tag);
    }

    @Test
    void blankTagIsRejectedWithFieldLevelMessage() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        loginAndNavigateTo(PAGE);
        fillAndWait(wait, "   ", "Saksofon 1", null);
        submitForm();

        // Validation happens server-side (the form submits with novalidate), so the banner
        // carries the @NotBlank field message from AddInstrumentRoleMapCommand.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("role-map-form-errors")));
        assertThat(driver.findElement(By.id("role-map-form-errors")).getText())
                .contains("Nazwa etykiety instrumentu jest wymagana");

        pollCount("SELECT COUNT(*) FROM instrument_role_map WHERE trim(source_tag) = ''", 0);
    }

    // ---------- helpers ----------

    /** Fills the add-mapping form (values exactly as a browser field would hand them over). */
    private void fillAndWait(WebDriverWait wait, String sourceTag, String targetRole, String description) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("instrument-roles-content")));
        typeInto("input[name='sourceTag']", sourceTag);
        typeInto("input[name='targetRolePattern']", targetRole);
        if (description != null) {
            typeInto("input[name='description']", description);
        }
    }

    private void typeInto(String cssSelector, String value) {
        var input = driver.findElement(By.cssSelector(cssSelector));
        input.clear();
        input.sendKeys(value);
    }

    /** JS-driven submit (click can be swallowed by HTML5 validation on CI runners — see RehearsalSaveUiTest). */
    private void submitForm() {
        ((JavascriptExecutor) driver).executeScript(
                "var f=document.getElementById('role-map-form');" +
                "f.noValidate=true;" +
                "f.requestSubmit();");
    }

    private WebElement waitUntilTagVisible(String tag) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        return wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//tr[td/code[contains(text(), '" + tag + "')]]")));
    }

    /** DB-polls until the committed count equals {@code expected} (the state the test contract cares about). */
    private void pollCount(String sql, int expected, Object... bindParams) {
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Integer actual = jdbcTemplate.queryForObject(sql, Integer.class, bindParams);
            assertThat(actual).as("DB count for SQL: " + sql).isEqualTo(expected);
        });
    }
}
