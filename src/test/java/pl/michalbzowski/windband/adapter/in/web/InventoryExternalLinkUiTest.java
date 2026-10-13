package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test for the WM -> windband-inventory menu integration (decision B11:
 * navigation is one-directional — WM shows a link to the external inventory
 * service; inventory never links back into WM pages).
 *
 * Requirements enforced here:
 *  1. The main menu ({@code #menu-modal}) exposes an "Inwentaryzacja" entry.
 *  2. It points at the EXTERNAL inventory service, not the legacy internal
 *     {@code /inventory} module.
 *  3. The external URL is environment-parameterized via the
 *     {@code app.inventory-url} property (prod: {@code INVENTORY_URL} env var,
 *     local: application-local.yml) — this test asserts the value configured in
 *     application-test.yml, proving the template reads the property, not a
 *     hard-coded path.
 *  4. It opens in a NEW TAB ({@code target="_blank"}) with {@code rel="noopener"}.
 *
 * UiTestBase already runs the app on a RANDOM_PORT with the "test" profile.
 */
@DisplayName("Menu: external inventory link opens windband-inventory in a new tab")
class InventoryExternalLinkUiTest extends UiTestBase {

    /** Value of app.inventory-url from src/test/resources/application-test.yml. */
    private static final String EXPECTED_URL = "http://inventory.test.local:5173/";

    @Test
    void menu_containsExternalInventoryLinkOpeningInNewTab() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        loginAndNavigateTo("/");

        WebElement link = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#menu-modal a#nav-inventory-external")));

        // The link lives inside a closed <dialog> (display:none), so Selenium's
        // getText() is empty — textContent reads the label from the DOM anyway.
        assertThat(link.getAttribute("textContent")).contains("Inwentaryzacja");
        assertThat(link.getAttribute("href")).isEqualTo(EXPECTED_URL);
        assertThat(link.getAttribute("target")).isEqualTo("_blank");
        assertThat(link.getAttribute("rel")).contains("noopener");
    }
}
