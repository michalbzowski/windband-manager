package pl.michalbzowski.windband.webui;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

/**
 * US-7.1 - the "Oznacz głosy na stronach nut" panel on the composition detail page:
 * one form (instrument + role + pageFrom/pageTo) to save a CompositionInstrument row,
 * plus one table listing all saved parts for quick visual confirmation.
 *
 * <p>Disabled on CI for the same reason as {@link CompositionDetailPageUiTest}:
 * the local test user is not a WindbandOidcUser bound to a team, so requireBandAccess
 * rejects before the panel renders. Manual QA (localhost) is the source of truth.
 */
class CompositionPartPanelUiTest extends UiTestBase {

    @Test
    @Disabled("requireBandAccess needs a real band-scoped OidcUser - disabled on CI like DashboardHomeUiTest")
    void partPanel_formAndTableArePresent() {
        var wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(5));
        // Navigate to the composition detail (band 1 seeded by data.sql).
        driver.get("/bands/1/compositions/1");

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#add-part-form")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#parts-panel")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#part-instrument-picker")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#add-part-form input[name=pageFrom]")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#add-part-form input[name=pageTo]")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("#clear-part-form-btn")));
    }
}
