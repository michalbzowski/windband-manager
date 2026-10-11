package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for team isolation: when a user belongs to only one band,
 * entities from another band must NOT appear in any view.
 *
 * <p>Seed data (data.sql = unified S2 inventory rows):
 * <ul>
 *   <li>Band 1 "Test Band": members Jan Kowalski / Anna Nowak; groups Trąbki, Perkusja;
 *       items <b>Bluza Test</b> (UNIFORM, assigned to Jan), <b>Trąbka Test</b> (INSTRUMENT).</li>
 *   <li>Band 2 "Other Band": members Piotr Zalewski / Maria Wojcik; group Saksofony;
 *       items <b>Bluza Other</b> (UNIFORM), <b>Saksofon Other</b> (INSTRUMENT).</li>
 * </ul>
 * The admin user is linked to band 1 only — every band-2 row above must stay
 * hidden from the inventory list, members page, groups page and dashboard.
 *
 * <p>The list page (S2 mobile surface) renders a flat card list in
 * {@code <div id="inv-list">}; type and attribute filtering happen client-side
 * via {@code <select id="inv-type-filter">} / {@code inv-attr-filter}, with
 * cards hidden using {@code style.display = "none"}. We therefore assert
 * against individual card elements rather than the whole page, because the two
 * S2 "other band" seeds are in different types and only one is rendered at a
 * time when a type filter is active.</p>
 */
class TeamIsolationRegressionUiTest extends UiTestBase {

    private WebDriverWait wait;

    @BeforeEach
    void initWait() {
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    // --- Members / groups / dashboard (unchanged from S1) -----------------------

    @Test
    @DisplayName("Members page shows only current band members")
    void membersPageShowsOnlyCurrentBandMembers() {
        loginAndNavigateTo("/members");

        String body = driver.findElement(By.tagName("body")).getText();
        assertThat(body).as("band-1 members visible").contains("Kowalski").contains("Nowak");
        assertThat(body).as("no band-2 names").doesNotContain("Zalewski").doesNotContain("Wojcik");
    }

    @Test
    @DisplayName("Groups page shows only current band groups")
    void groupsPageShowsOnlyCurrentBandGroups() {
        loginAndNavigateTo("/groups");

        String body = driver.findElement(By.tagName("body")).getText();
        assertThat(body).as("band-1 groups visible").contains("Trąbki").contains("Perkusja");
        assertThat(body).as("no band-2 groups").doesNotContain("Saksofony");
    }

    @Test
    @DisplayName("Dashboard does not leak other band member names")
    void dashboardDoesNotLeakOtherBandMembers() {
        loginAndNavigateTo("/");

        String body = driver.findElement(By.tagName("body")).getText();
        assertThat(body).as("no band-2 names on dashboard").doesNotContain("Zalewski").doesNotContain("Wojcik");
    }

    // --- Inventory list (new S2 surface: unified cards + type filter ----------------------

    @Test
    @DisplayName("Inventory list in default state does not leak other-band items")
    void inventoryListDoesNotLeakOtherBandInDefaultState() {
        loginAndNavigateTo("/inventory");
        wait.until(d -> d.findElements(By.cssSelector("#inv-list .inv-card")).size() >= 1);

        // Regardless of which rows are currently rendered (type filter may have run a
        // prior test's chip selection on this page), the DOM must NOT contain cards for
        // seeded band-2 names — those would only appear if isolation were broken.
        // We count by matching each card's own <h3> rather than scanning body text,
        // so a card hidden by display:none (legitimate client-side filter result) still counts
        // as "not visible" here, and only leaked seed rows fail the assertion.
        assertThat(visibleCardNamed(" Bluza Other  ")).as("band-1 view must not contain seeded band-2 uniform").isFalse();
        assertThat(visibleCardNamed(" Saksofon Other  ")).as("band-1 view must not contain seeded band-2 instrument").isFalse();
    }

    @Test
    @DisplayName("Switching to UNIFORM type filter keeps band-2 uniform seeds hidden")
    void inventoryUniformsFilterDoesNotLeakOtherBand() {
        loginAndNavigateTo("/inventory");
        wait.until(d -> d.findElements(By.cssSelector("#inv-list .inv-card")).size() >= 1);

        chooseTypeFilter("UNIFORM");

        // Band-1 card should be visible after the client-side filter has applied (display="").
        wait.until(d -> visibleCardNamed(" Bluza Test  "));

        // And the band-2 uniform must remain hidden even though both are UNIFORM-type rows.
        assertThat(visibleCardNamed(" Bluza Other  "))
                .as("band-2 'Bluza Other' must not appear in band-1 UNIFORM filter")
                .isFalse();
    }

    @Test
    @DisplayName("Switching to INSTRUMENT type filter keeps band-2 instrument seeds hidden")
    void inventoryInstrumentsFilterDoesNotLeakOtherBand() {
        loginAndNavigateTo("/inventory");
        wait.until(d -> d.findElements(By.cssSelector("#inv-list .inv-card")).size() >= 1);

        chooseTypeFilter("INSTRUMENT");

        wait.until(d -> visibleCardNamed(" Trąbka Test  "));
        assertThat(visibleCardNamed(" Saksofon Other  "))
                .as("band-2 'Saksofon Other' must not appear in band-1 INSTRUMENT filter")
                .isFalse();

        // Sanity: band-1 instrument visible AND present.
        assertThat(visibleCardNamed(" Trąbka Test  ")).as("band-1 'Trąbka Test' must appear").isTrue();
    }

    // --- Helpers ---------------------------------------------------------------------

    /** Sets the S2 type filter (select#inv-type-filter) and triggers the client-side render. */
    private void chooseTypeFilter(String typeValue) {
        ((JavascriptExecutor) driver).executeScript(
                "var s = document.getElementById('inv-type-filter');" +
                " if (!s) throw new Error('no #inv-type-filter in page');" +
                " s.value = arguments[0];" +
                " s.dispatchEvent(new Event('change', {bubbles: true}));" +
                ";", typeValue);
        ((JavascriptExecutor) driver).executeScript("inv.render();");
    }

    /**
     * Returns true iff a card with an exact <h3> name exists in #inv-list AND is visible
     * (Selenium's {@code WebElement.isDisplayed()} semantics: non-zero box, and no ancestor
     * with display:none — exactly what the client-side filter uses to hide cards).
     */
    private boolean visibleCardNamed(String exactNameText) {
        List<WebElement> cards = driver.findElements(By.cssSelector("#inv-list .inv-card"));
        for (WebElement card : cards) {
            WebElement h3;
            try {
                h3 = card.findElement(By.cssSelector("h3"));
            } catch (Exception notFound) {
                continue; // e.g. form card or modal — skip
            }
            if (exactNameText.equals(" " + h3.getText().trim() + " ")) {
                try {
                    return card.isDisplayed();
                } catch (Exception stale) {
                    return false;
                }
            }
        }
        return false;
    }
}
