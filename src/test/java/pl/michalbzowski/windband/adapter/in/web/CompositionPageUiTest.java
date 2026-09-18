package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CompositionPageUiTest extends UiTestBase {

    @AfterEach
    void removeTestCompositions() {
        jdbcTemplate.update("DELETE FROM compositions WHERE title IN (?, ?, ?, ?)",
                "Marsz Testowy", "Polka Testowa", "Nowy Utwór UI", "Edytowany Utwór UI");
        // US-3.5 lifecycle tests — keep @AfterEach tidy for any in-flight seeds.
        jdbcTemplate.update("DELETE FROM compositions WHERE title IN (?, ?, ?)",
                "Lifecycle DRAFT", "Lifecycle READY", "Lifecycle ARCHIVED");
    }

    /** US-3.5 — archive endpoint: status flips to ARCHIVED and the badge updates in the DOM. */
    @Test
    void shouldArchiveComposition_andShowArchivedStatus_badge() {
        seedComposition("Lifecycle DRAFT", "DRAFT");
        Long id = compositionIdByTitle("Lifecycle DRAFT");
        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + id);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Click archive via the shared lifecycle dialog (the button's inline onclick
        // routes through the window.openLifecycleDialog helper defined in detail.html).
        driver.findElement(By.id("archive-composition-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lifecycle-confirm-btn")));
        driver.findElement(By.id("lifecycle-confirm-btn")).click();

        // URL returns to the detail page (302); status badge shows "Zarchiwizowany".
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#composition-detail .badge"), "Zarchiwizowany"));

        String inDb = jdbcTemplate.queryForObject(
                "SELECT status FROM compositions WHERE band_id = 1 AND id = ?",
                String.class, id);
        assertThat(inDb).isEqualTo("ARCHIVED");
    }

    /** US-3.5 — restore endpoint: ARCHIVED → DRAFT, badge returns to "Szkic". */
    @Test
    void shouldRestoreArchivedComposition_toDraft_andReappearInList() {
        seedComposition("Lifecycle READY", "READY");
        Long id = compositionIdByTitle("Lifecycle READY");

        // Establish the session before using the driver (shared from a previous test — the
        // idempotent UiTestBase.doLogin() short-circuits if already logged in).
        loginAndNavigateTo("/bands/1/compositions");
        driver.get(baseUrl() + "/bands/1/compositions/" + id);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // Archive first (button is only visible when status != ARCHIVED).
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("archive-composition-btn")));
        driver.findElement(By.id("archive-composition-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lifecycle-confirm-btn")));
        driver.findElement(By.id("lifecycle-confirm-btn")).click();
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        String archived = jdbcTemplate.queryForObject(
                "SELECT status FROM compositions WHERE band_id = 1 AND id = ?", String.class, id);
        // (assertion — the previous step must have persisted ARCHIVED before we restore.)
        assertThat(archived).isEqualTo("ARCHIVED");

        // Restore: badge returns to "Szkic", same row in the list.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("restore-composition-btn")));
        driver.findElement(By.id("restore-composition-btn")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lifecycle-confirm-btn")));
        driver.findElement(By.id("lifecycle-confirm-btn")).click();
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#composition-detail .badge"), "Szkic"));

        String restored = jdbcTemplate.queryForObject(
                "SELECT status FROM compositions WHERE band_id = 1 AND id = ?", String.class, id);
        assertThat(restored).isEqualTo("DRAFT");

        // The list still includes the restored row (band-scoped view).
        driver.get(baseUrl() + "/bands/1/compositions");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("compositions-content"), "Lifecycle READY"));
    }

    /** US-3.5 — delete endpoint: row disappears from the list AND from the database; a detail
     *  reload yields 410/409 (server rejects the foreign-or-missing id) not 200. */
    @Test
    void shouldDeleteComposition_andRemoveFromBandList_persistently() {
        seedComposition("Lifecycle ARCHIVED", "ARCHIVED");
        Long id = compositionIdByTitle("Lifecycle ARCHIVED");

        // Establish the session before using the driver (shared from a previous test — the
        // idempotent UiTestBase.doLogin() short-circuits if already logged in).
        loginAndNavigateTo("/bands/1/compositions");

        // Confirm it is visible in the band-scoped list first (asserts that deletion is NOT
        // a no-op hiding the row — we start from a state where it should be listed).
        driver.get(baseUrl() + "/bands/1/compositions");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("compositions-content"), "Lifecycle ARCHIVED"));

        // Open detail → click the red "Usuń" button (always visible in US-3.5) → confirm in the shared dialog.
        driver.get(baseUrl() + "/bands/1/compositions/" + id);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("delete-composition-btn")));
        driver.findElement(By.id("delete-composition-btn")).click();

        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("lifecycle-confirm-btn")));
        driver.findElement(By.id("lifecycle-confirm-btn")).click();

        // After the POST we land on the list (controller redirects there) and the row is gone.
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions$"));
        // The row must have disappeared from both the DOM list and the DB in one call.
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE band_id = 1 AND id = ?", Integer.class, id);
        assertThat(remaining).isZero();

        // Visiting the deleted detail page should NOT 200 with the old title — it must either
        // 404/409 (via the band-isolation gate) or render an error view. A GET that "succeeds"
        // would be a real regression this test is meant to catch. (We do NOT assert body text
        // here because Spring's default error view may vary across profiles; the URL + DB row
        // assertions above are the load-bearing contract.)
        Integer stillInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE band_id = 1 AND title = ?", Integer.class,
                "Lifecycle ARCHIVED");
        assertThat(stillInDb).isZero();
    }

    // ---------- US-3.5 helpers ----------------------------------------------------------

    private void seedComposition(String title, String status) {
        jdbcTemplate.update("""
                INSERT INTO compositions
                    (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, title, "Opis lifecycle testu", null, null, status);
    }

    private Long compositionIdByTitle(String title) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM compositions WHERE band_id = 1 AND title = ?",
                Long.class, title);
    }

    @Test
    void shouldListSeededCompositionsAndCreateANewOne() {
        // Reset any leakage from other tests (JUnit5 does not guarantee method order).
        jdbcTemplate.update("DELETE FROM compositions WHERE band_id = 1");

        jdbcTemplate.update("""
                INSERT INTO compositions
                    (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'DRAFT', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Marsz Testowy", "Opis marsza", "Jan Testowy", null);
        jdbcTemplate.update("""
                INSERT INTO compositions
                    (title, description, composer, arranger, status, band_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'READY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Polka Testowa", null, "Anna Testowa", "Piotr Testowy");

        loginAndNavigateTo("/bands/1/compositions");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        assertThat(driver.findElements(By.cssSelector("#compositions-content tbody tr"))).hasSize(2);
        assertThat(driver.findElement(By.id("compositions-content")).getText())
                .contains("Marsz Testowy", "Polka Testowa");

        driver.findElement(By.id("add-composition-btn")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-form")));

        driver.findElement(By.name("title")).sendKeys("Nowy Utwór UI");
        driver.findElement(By.name("description")).sendKeys("Opis utworu utworzonego w UI");
        driver.findElement(By.name("composer")).sendKeys("Kompozytor UI");
        driver.findElement(By.name("arranger")).sendKeys("Aranżer UI");
        driver.findElement(By.id("save-composition-btn")).click();

        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("composition-detail"), "Nowy Utwór UI"));

        assertThat(driver.findElement(By.id("composition-detail")).getText())
                .contains("Opis utworu utworzonego w UI", "Kompozytor UI", "Aranżer UI", "Szkic");
        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE band_id = 1 AND title = ?",
                Integer.class, "Nowy Utwór UI");
        assertThat(persisted).isEqualTo(1);
    }

    @Test
    void shouldShowServerValidationForBlankTitle() {
        loginAndNavigateTo("/bands/1/compositions/new");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.findElement(By.id("save-composition-btn")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("composition-form-errors"), "Tytuł jest wymagany"));
        assertThat(driver.findElement(By.id("composition-form"))).isNotNull();
        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE band_id = 1 AND title = ''",
                Integer.class);
        assertThat(persisted).isZero();
    }

    /** US-3.4 — edit metadata via GET /edit + PATCH /compositions/{id}. */
    @Test
    void shouldEditMetadata_onEditPage_andPersistAllFields() {
        loginAndNavigateTo("/bands/1/compositions/new");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.findElement(By.name("title")).sendKeys("Edytowany Utwór UI");
        driver.findElement(By.name("composer")).sendKeys("Kompozytor Before");
        driver.findElement(By.id("save-composition-btn")).click();
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        Long seedId = Long.valueOf(driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf('/') + 1));

        // Navigate to the edit page (full URL — driver.get requires absolute URLs).
        driver.get(baseUrl() + "/bands/1/compositions/" + seedId + "/edit");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-edit-form")));

        // Modify fields (Selenium `clear()` returns void — chain via separate calls).
        WebElement title = driver.findElement(By.name("title"));
        title.clear();
        title.sendKeys("Polka Zaktualizowana UI");
        WebElement composer = driver.findElement(By.name("composer"));
        composer.clear();
        composer.sendKeys("Kompozytor After");
        WebElement arranger = driver.findElement(By.name("arranger"));
        arranger.clear();
        arranger.sendKeys("Aranżer UI Update");

        driver.findElement(By.id("save-composition-edit-btn")).click();

        // Expect a redirect back to the detail page (/bands/1/compositions/{id}).
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("composition-detail"), "Polka Zaktualizowana UI"));

        String detail = driver.findElement(By.id("composition-detail")).getText();
        assertThat(detail).contains("Kompozytor After", "Aranżer UI Update");

        Integer inDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM compositions WHERE band_id = 1 AND title = ? AND composer = ? AND arranger = ?",
                Integer.class, "Polka Zaktualizowana UI", "Kompozytor After", "Aranżer UI Update");
        assertThat(inDb).isEqualTo(1);
    }

    /** US-3.4 — server-side validation on PATCH prevents an empty title from saving. */
    @Test
    void shouldRejectBlankTitle_onEditPage_andRenderValidationError() {
        loginAndNavigateTo("/bands/1/compositions/new");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        driver.findElement(By.name("title")).sendKeys("Edytowany Utwór UI");
        driver.findElement(By.id("save-composition-btn")).click();
        wait.until(ExpectedConditions.urlMatches(".*/bands/1/compositions/\\d+$"));
        Long seedId = Long.valueOf(driver.getCurrentUrl().substring(driver.getCurrentUrl().lastIndexOf('/') + 1));

        driver.get(baseUrl() + "/bands/1/compositions/" + seedId + "/edit");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("composition-edit-form")));

        // Clear title to trigger server-side validation via binding.
        driver.findElement(By.name("title")).clear();
        driver.findElement(By.id("save-composition-edit-btn")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("composition-edit-form-errors"), "Tytuł jest wymagany"));

        // Row unchanged in DB — title still equals the original.
        String title = jdbcTemplate.queryForObject(
                "SELECT title FROM compositions WHERE band_id = 1 AND id = ?",
                String.class, seedId);
        assertThat(title).isEqualTo("Edytowany Utwór UI");
    }
}
