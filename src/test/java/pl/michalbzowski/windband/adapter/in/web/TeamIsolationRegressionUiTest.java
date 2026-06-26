package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for team isolation: when a user belongs to only one band,
 * entities from another band must NOT appear in any view.
 *
 * Seed data (data.sql):
 * - Band 1: "Test Band" with members Jan Kowalski, Anna Nowak; groups Trąbki, Perkusja;
 *   uniforms Bluza Test; instruments Trąbka Test; awards Medal za zasługi
 * - Band 2: "Other Band" with members Piotr Zalewski, Maria Wojcik; groups Saksofony;
 *   uniforms Bluza Other; instruments Saksofon Other; awards Medal innego zespołu
 * - User "admin" belongs only to band 1
 */
class TeamIsolationRegressionUiTest extends UiTestBase {

    private WebDriverWait wait;

    @BeforeEach
    void initWait() {
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Members page shows only current band members")
    void membersPageShowsOnlyCurrentBandMembers() {
        loginAndNavigateTo("/members");

        String page = getPageText();

        // Should see Test Band members
        assertThat(page).contains("Kowalski");
        assertThat(page).contains("Nowak");

        // Must NOT see Other Band members
        assertThat(page).doesNotContain("Zalewski");
        assertThat(page).doesNotContain("Wojcik");
    }

    @Test
    @DisplayName("Groups page shows only current band groups")
    void groupsPageShowsOnlyCurrentBandGroups() {
        loginAndNavigateTo("/groups");

        String page = getPageText();

        // Should see Test Band groups
        assertThat(page).contains("Trąbki");
        assertThat(page).contains("Perkusja");

        // Must NOT see Other Band groups
        assertThat(page).doesNotContain("Saksofony");
    }

    @Test
    @DisplayName("Inventory uniforms tab does not leak other band items")
    void inventoryUniformsDoesNotLeakOtherBand() {
        loginAndNavigateTo("/inventory");

        // Click Stroje (Uniforms) tab
        clickJsTab("uniforms");
        waitForTabContent("tab-uniforms");

        String page = getPageText();

        // Must NOT see Other Band items
        assertThat(page).doesNotContain("Bluza Other");
    }

    @Test
    @DisplayName("Inventory instruments tab does not leak other band items")
    void inventoryInstrumentsDoesNotLeakOtherBand() {
        loginAndNavigateTo("/inventory");

        // Click Instrumenty tab
        clickJsTab("instruments");
        waitForTabContent("tab-instruments");

        String page = getPageText();

        // Must NOT see Other Band items
        assertThat(page).doesNotContain("Saksofon Other");
    }

    @Test
    @DisplayName("Inventory awards tab does not leak other band awards")
    void inventoryAwardsDoesNotLeakOtherBand() {
        loginAndNavigateTo("/inventory");

        // Click Odznaczenia tab
        clickJsTab("awards");
        waitForTabContent("tab-awards");

        String page = getPageText();

        // Must NOT see Other Band awards
        assertThat(page).doesNotContain("Medal innego");
        assertThat(page).doesNotContain("zespołu");
    }

    @Test
    @DisplayName("Dashboard does not leak other band member names")
    void dashboardDoesNotLeakOtherBandMembers() {
        loginAndNavigateTo("/");

        String page = getPageText();

        // Other Band members must never appear on the dashboard
        assertThat(page).doesNotContain("Zalewski");
        assertThat(page).doesNotContain("Wojcik");
    }

    // === Helper methods ===

    private String getPageText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    /** Click tab using the JS switchTab function defined in the inventory template */
    private void clickJsTab(String tabName) {
        ((JavascriptExecutor) driver).executeScript("switchTab('" + tabName + "')");
    }

    /** Wait for a tab div to become visible */
    private void waitForTabContent(String tabId) {
        wait.until(d -> {
            WebElement el = d.findElement(By.id(tabId));
            return el != null && el.isDisplayed();
        });
    }
}
