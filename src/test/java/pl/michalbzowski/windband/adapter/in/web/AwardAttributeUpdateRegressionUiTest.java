package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Regression test for the bug where updating an AWARD attribute definition
 * returned 400 Bad Request because the update endpoint was missing the AWARD case
 * in the switch statement.
 *
 * This test verifies that:
 * 1. An AWARD attribute can be created
 * 2. The AWARD attribute can be edited (updated) via the edit form
 * 3. The updated values are persisted and visible on the list
 */
class AwardAttributeUpdateRegressionUiTest extends UiTestBase {

    @Value("${local.server.port:8080}")
    private String port;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void loginBeforeTest() {
        // Login with admin credentials before each test
        driver.get(baseUrl() + "/login");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.name("username")));

        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for redirect after login (no longer on /login page)
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
    }

    @Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardItemRepository awardItemRepo;

    @Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardAttributeValueRepository awardAttrValueRepo;

    @Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardAttributeDefRepository awardAttrRepo;

    @Autowired
    private pl.michalbzowski.windband.domain.band.BandRepository bandRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String unique;
    private Set<Long> awardIdsBefore;

    @BeforeEach
    void snapshotState() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        awardIdsBefore = idsOf(awardItemRepo.findByBandIdOrderByDateAwardedDescNameAsc(1L));
    }

    @AfterEach
    void cleanup() {
        // Delete awards created by this test
        deleteNewAwards(awardIdsBefore);
        // Delete attribute defs we created
        pl.michalbzowski.windband.domain.band.Band band = bandRepo.findById(1L).orElse(null);
        if (band != null) {
            awardAttrRepo.findByBandAndActiveTrueOrderByDisplayOrderAsc(band).stream()
                .filter(d -> ("AwAttr" + unique).equals(d.getName()))
                .findFirst().ifPresent(d -> awardAttrRepo.delete(d));
        }
    }

    private static Set<Long> idsOf(Iterable<?> items) {
        Set<Long> ids = new HashSet<>();
        items.forEach(i -> {
            try {
                Object idObj = i.getClass().getMethod("getId").invoke(i);
                if (idObj instanceof Long id && id != null) {
                    ids.add(id);
                }
            } catch (Exception ignored) {
                // intentionally ignored
            }
        });
        return ids;
    }

    private void deleteNewAwards(Set<Long> before) {
        awardItemRepo.findByBandIdOrderByDateAwardedDescNameAsc(1L).stream()
            .filter(a -> !before.contains(a.getId()))
            .forEach(a -> {
                awardAttrValueRepo.findByAwardItemId(a.getId()).forEach(awardAttrValueRepo::delete);
                awardItemRepo.delete(a);
            });
    }

    @Test
    void awardAttribute_shouldBeUpdatableViaEditForm() {
        String attrName = "AwAttr" + unique;
        String updatedName = "AwAttrUpdated" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // === 1. Create AWARD attribute via UI ===
        createInventoryAttributeViaUI("AWARD", attrName, "TEXT", wait);

        // === 2. Verify on the attributes list ===
        assertAttributeVisibleOnList("AWARD", attrName, wait);

        // === 3. Open the edit form for this attribute ===
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        // Wait for tabs to be initialized and AWARD tab to be visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("tab-awards")));

        // Find the edit button in the visible awards table
        var editButton = driver.findElement(By.xpath(
            "//div[@id='tab-awards']//tr[.//text()[contains(., '" + attrName + "')]]//button[contains(., 'Edytuj')]"
        ));

        // Use JavaScriptExecutor to click the button (more reliable with HTMX)
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editButton);

        // Wait for edit form to load after HTMX swap
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        // === 4. Update the attribute name and display order ===
        var nameInput = driver.findElement(By.cssSelector("input[name='name']"));
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = ''; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));", nameInput);
        nameInput.sendKeys(updatedName);

        var displayOrderInput = driver.findElement(By.cssSelector("input[name='displayOrder']"));
        ((JavascriptExecutor) driver).executeScript(
            "arguments[0].value = '99'; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));", displayOrderInput);

        // Submit the edit form using HTMX-triggered submission
        ((JavascriptExecutor) driver).executeScript(
            "var form = document.querySelector('form[hx-put]');if (form){htmx.trigger(form,'submit')}");

        // Wait for redirect back to list via URL change (HTMX uses HX-Redirect header)
        wait.until(ExpectedConditions.urlContains("/band/inventory-attributes?type=AWARD"));

        // === 5. Verify the updated attribute appears on the list with new name ===
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        var pageSource = driver.getPageSource();
        assert pageSource.contains(updatedName) : "Updated attribute name must be visible on list";

        // === 6. Verify the display order was updated ===
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        var editButton2 = driver.findElement(By.xpath(
            "//tr[.//text()[contains(., '" + updatedName + "')]]//button[contains(., 'Edytuj')]"
        ));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editButton2);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        var nameInput2 = driver.findElement(By.cssSelector("input[name='name']"));
        assert nameInput2.getAttribute("value").equals(updatedName) : "Updated name must persist in edit form";

        var displayOrderInput2 = driver.findElement(By.cssSelector("input[name='displayOrder']"));
        assert displayOrderInput2.getAttribute("value").equals("99") : "Display order must be 99";
    }

    // Helper methods from AttributeFlowUiTest

    /**
     * Opens the new attribute form, fills it, submits, and waits for the HX-Redirect URL change.
     */
    private void createInventoryAttributeViaUI(String type, String name, String attrType, WebDriverWait wait) {
        driver.get(baseUrl() + "/band/inventory-attributes/new?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        JavascriptExecutor js = (JavascriptExecutor) driver;

        var nameInput = driver.findElement(By.cssSelector("input[name='name']"));
        js.executeScript(
            "arguments[0].value = ''; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));",
            nameInput);
        nameInput.sendKeys(name);

        // Use JavaScript to handle select option (works better with dynamic HTMX pages)
        ((JavascriptExecutor) driver).executeScript(
            "var sel = document.querySelector('select[name=\"attributeType\"]');" +
            "for (var opt of sel.options) {" +
            "  if (opt.value === '" + attrType + "') { opt.selected = true; break; }" +
            "}" +
            "sel.dispatchEvent(new Event('change', {bubbles:true}));"
        );

        var displayOrderInput = driver.findElement(By.cssSelector("input[name='displayOrder']"));
        js.executeScript(
            "arguments[0].value = '1'; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));",
            displayOrderInput);

        // Use JavaScript to click the checkbox (avoids sticky header issue)
        ((JavascriptExecutor) driver).executeScript("document.querySelector(\"input[name='displayInList']\").click();");
        ((JavascriptExecutor) driver).executeScript(
            "document.querySelector(\"input[name='displayInList']\").checked = true;");

        var submitBtn = driver.findElement(By.cssSelector("form button[type='submit'].primary"));
        js.executeScript("arguments[0].click();", submitBtn);

        // Wait for redirect using URL change
        wait.until(ExpectedConditions.urlContains("/band/inventory-attributes?type=" + type));
    }

    private void assertAttributeVisibleOnList(String type, String name, WebDriverWait wait) {
        driver.get(baseUrl() + "/band/inventory-attributes?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector("#content"), name));
    }
}
