package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #271 — in dark mode the three-dot (⋮) button on a meeting/rehearsal
 * detail view was invisible, and so were its menu items (only "Usuń" showed,
 * because its red was hard-coded).
 *
 * <p>The app never sets {@code [data-theme]} nor {@code body.dark-mode}: Pico
 * v2 (CDN) simply follows the OS through {@code prefers-color-scheme}. The
 * page-header colours were hard-coded to {@code #1a1a1a} "for the light theme"
 * and their dark overrides could therefore never apply — dark ink on a dark
 * surface.
 *
 * <p>The test emulates the dark scheme in Chrome and asserts that the trigger
 * and the menu items compute to a LIGHT text colour.
 */
class DetailMenuDarkThemeUiTest extends UiTestBase {

    @Test
    void overflowMenuIsVisibleInDarkColorScheme() {
        loginOnly();
        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);
        Long rehearsalId = createRehearsalViaApi("Dark " + uid, LocalDate.now().plusDays(3));
        assertThat(rehearsalId).isNotNull();

        // Ask Chrome to report the dark colour scheme, exactly like an OS in dark mode.
        ((ChromeDriver) driver).executeCdpCommand("Emulation.setEmulatedMedia", Map.of(
                "features", List.of(Map.of("name", "prefers-color-scheme", "value", "dark"))));

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        WebElement toggle = wait.until(d ->
                d.findElement(By.cssSelector(".icon-btn[data-detail-action='toggle-more']")));

        // Guard: the emulation must really be active, otherwise the assertion below
        // would prove nothing about dark mode.
        assertThat(js.executeScript("return window.matchMedia('(prefers-color-scheme: dark)').matches;"))
                .as("browser reports the dark colour scheme")
                .isEqualTo(true);

        assertThat(isLightText("return getComputedStyle(arguments[0]).color;", toggle))
                .as("the ⋮ trigger is light ink on the dark surface (issue #271)")
                .isTrue();

        js.executeScript("arguments[0].click();", toggle);
        wait.until(d -> {
            WebElement menu = d.findElement(By.cssSelector(".overflow-menu"));
            return Boolean.TRUE.equals(js.executeScript(
                    "return getComputedStyle(arguments[0]).display !== 'none';", menu));
        });

        List<WebElement> items = driver.findElements(By.cssSelector(".overflow-menu .overflow-item"));
        assertThat(items).as("menu items are rendered").isNotEmpty();
        for (WebElement item : items) {
            String label = item.getText().trim();
            // The destructive entry is red by design (that is why the reporter could
            // see it at all); everything else has to be readable light ink.
            if (label.toLowerCase().contains("usuń")) {
                continue;
            }
            assertThat(isLightText("return getComputedStyle(arguments[0]).color;", item))
                    .as("menu item '%s' is visible in dark mode", label)
                    .isTrue();
        }
    }

    /** True when the computed rgb() colour is light enough to read on a dark panel. */
    private boolean isLightText(String script, WebElement element) {
        Object raw = ((JavascriptExecutor) driver).executeScript(script, element);
        String color = String.valueOf(raw); // e.g. "rgb(230, 230, 230)"
        String digits = color.replaceAll("[^0-9,]", "");
        String[] parts = digits.split(",");
        if (parts.length < 3) {
            return false;
        }
        int threshold = 120;
        return Integer.parseInt(parts[0].trim()) >= threshold
                && Integer.parseInt(parts[1].trim()) >= threshold
                && Integer.parseInt(parts[2].trim()) >= threshold;
    }
}
