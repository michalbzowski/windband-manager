package pl.michalbzowski.windband.adapter.in.web;

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
 * Reproduces and verifies the fix for issue #80:
 * "Wejście na widok atrybutów wyświetla atrybuty ze wszystkich zakładek na raz.
 *  Klikanie w zakładkę nie działa."
 *
 * <p>Expected behaviour after fix:
 * <ul>
 *   <li>Only the first (active) tab content is visible on page load</li>
 *   <li>Clicking a different tab shows only that tab's content</li>
 *   <li>Other tab contents are hidden</li>
 * </ul>
 */
class AttributesTabUiTest extends UiTestBase {

    @Test
    void onlyActiveTabContentIsVisible_onPageLoad() {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("tab-uniforms")));

        // The active tab (uniforms) must be visible
        assertPanelVisible("#tab-uniforms");

        // All other tabs must be hidden
        assertPanelHidden("#tab-instruments");
        assertPanelHidden("#tab-orders");
        assertPanelHidden("#tab-awards");
        assertPanelHidden("#tab-members");
    }

    @Test
    void clickingTabShowsOnlyThatTabsContent() throws InterruptedException {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("tab-uniforms")));

        // Initially uniforms is visible, instruments hidden
        assertPanelVisible("#tab-uniforms");
        assertPanelHidden("#tab-instruments");

        // Click the "instruments" tab button
        WebElement instrumentsBtn = driver.findElement(By.cssSelector(".tab-btn[data-tab='instruments']"));
        instrumentsBtn.click();

        // Wait for the DOM update from the JS click handler
        Thread.sleep(500);

        // Now instruments should be visible and uniforms hidden
        assertPanelVisible("#tab-instruments");
        assertPanelHidden("#tab-uniforms");
        assertPanelHidden("#tab-orders");
        assertPanelHidden("#tab-awards");
        assertPanelHidden("#tab-members");
    }

    @Test
    void clickingEachTab_showsOnlyThatTab() throws InterruptedException {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("tab-uniforms")));

        String[] tabs = {"uniforms", "instruments", "orders", "awards", "members"};

        for (String tab : tabs) {
            // Click the tab button
            WebElement btn = driver.findElement(By.cssSelector(".tab-btn[data-tab='" + tab + "']"));
            btn.click();
            Thread.sleep(500);

            // The clicked tab must be visible
            assertPanelVisible("#tab-" + tab);

            // All other tabs must be hidden
            for (String other : tabs) {
                if (!other.equals(tab)) {
                    assertPanelHidden("#tab-" + other);
                }
            }
        }
    }

    @Test
    void activeTabButtonHasPrimaryClass() throws InterruptedException {
        loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("tab-uniforms")));

        // Initially the "uniforms" button should have the primary class
        WebElement uniformsBtn = driver.findElement(By.cssSelector(".tab-btn[data-tab='uniforms']"));
        assertThat(uniformsBtn.getAttribute("class")).contains("primary");

        // Click "members" tab
        WebElement membersBtn = driver.findElement(By.cssSelector(".tab-btn[data-tab='members']"));
        membersBtn.click();
        Thread.sleep(500);

        // Now "members" should have primary and "uniforms" should not
        assertThat(membersBtn.getAttribute("class")).contains("primary");
        assertThat(uniformsBtn.getAttribute("class")).doesNotContain("primary");
    }

    private void assertPanelVisible(String cssSelector) {
        WebElement el = driver.findElement(By.cssSelector(cssSelector));
        assertThat(el).isNotNull();
        Object display = ((JavascriptExecutor) driver).executeScript(
                "var e = document.querySelector(arguments[0]);" +
                "return window.getComputedStyle(e).display;", cssSelector);
        assertThat(display)
                .as("Element %s must be visible (display != 'none')", cssSelector)
                .isNotEqualTo("none");
    }

    private void assertPanelHidden(String cssSelector) {
        WebElement el = driver.findElement(By.cssSelector(cssSelector));
        assertThat(el).isNotNull();
        Object display = ((JavascriptExecutor) driver).executeScript(
                "var e = document.querySelector(arguments[0]);" +
                "return window.getComputedStyle(e).display;", cssSelector);
        assertThat(display)
                .as("Element %s must be hidden (display == 'none')", cssSelector)
                .isEqualTo("none");
    }
}
