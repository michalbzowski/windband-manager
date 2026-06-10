package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.inventory.UniformAttributeCommandService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDefRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the inventory attributes page shows ALL five type sections
 * (uniforms, instruments, orders, awards, members) at the same time,
 * regardless of the {@code ?type=...} URL parameter.
 *
 * <p>Previously the template used {@code th:style="${type != 'UNIFORM'}" ? 'display:none;' : ''}
 * on each tab, so navigating from the nav menu (which uses {@code ?type=MEMBER}) showed
 * only the empty "Members" section and hid the user's actual uniform attributes.</p>
 */
class InventoryAttributesUiTest extends UiTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private UniformAttributeCommandService uniformCommandService;

    @org.springframework.beans.factory.annotation.Autowired
    private UniformAttributeDefRepository uniformAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private BandRepository bandRepo;

    @Test
    void allFiveTabsAreVisibleSimultaneously() {
        loginAndNavigateTo("/band/attributes?type=MEMBER");

        // All 5 tab buttons are present
        assertThat(driver.findElement(By.xpath("//button[@data-tab='uniforms']"))).isNotNull();
        assertThat(driver.findElement(By.xpath("//button[@data-tab='instruments']"))).isNotNull();
        assertThat(driver.findElement(By.xpath("//button[@data-tab='orders']"))).isNotNull();
        assertThat(driver.findElement(By.xpath("//button[@data-tab='awards']"))).isNotNull();
        assertThat(driver.findElement(By.xpath("//button[@data-tab='members']"))).isNotNull();

        // All 5 tab content panels are present AND visible (display != 'none')
        assertVisible("#tab-uniforms");
        assertVisible("#tab-instruments");
        assertVisible("#tab-orders");
        assertVisible("#tab-awards");
        assertVisible("#tab-members");

        // The tab button whose data-tab matches ?type= gets the 'primary' class
        var membersBtn = driver.findElement(By.xpath("//button[@data-tab='members']"));
        assertThat(membersBtn.getAttribute("class")).contains("primary");
    }

    /**
     * Regression test for the reported bug: "I have uniform attributes but the list
     * is empty after login. Only after entering the new-attribute view and cancelling
     * do I see the attributes." With the fix (all sections visible), uniform attributes
     * must be visible right after navigating to {@code /band/attributes?type=MEMBER}
     * (the link from the nav menu).
     */
    @Test
    void uniformAttributesAreVisibleEvenWhenNavigatedToWithTypeMember() {
        // Seed a uniform attribute directly via service so the test is deterministic
        Band band = bandRepo.findAll().stream()
                .filter(b -> "Test Band".equals(b.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Test Band not seeded"));
        String uniqueName = "RozmiarKoszuli-" + UUID.randomUUID().toString().substring(0, 6);
        uniformCommandService.createAttributeDef(
                band, uniqueName, "TEXT", false, true, 0, null, null, null);
        UniformAttributeDef created = uniformAttrRepo.findByBand(band).stream()
                .filter(d -> uniqueName.equals(d.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created attribute not found"));

        try {
            // Navigate to /band/attributes — same URL as the nav menu link
            loginAndNavigateTo("/band/attributes?type=MEMBER");

            // The tab-uniforms section must be visible (it was hidden by the old style)
            assertVisible("#tab-uniforms");

            // And the freshly created uniform attribute must be in the visible uniforms table
            String uniformsContent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return document.querySelector('#tab-uniforms').textContent;");
            assertThat(uniformsContent)
                    .as("uniform attribute '%s' must appear in #tab-uniforms even when type=MEMBER", uniqueName)
                    .contains(uniqueName);
        } finally {
            uniformAttrRepo.delete(created);
        }
    }

    private void assertVisible(String cssSelector) {
        WebElement el = driver.findElement(By.cssSelector(cssSelector));
        assertThat(el).isNotNull();
        Object display = ((JavascriptExecutor) driver).executeScript(
                "var e = document.querySelector(arguments[0]);" +
                "return window.getComputedStyle(e).display;", cssSelector);
        assertThat(display)
                .as("Element %s must be visible (display != 'none')", cssSelector)
                .isNotEqualTo("none");
    }
}
