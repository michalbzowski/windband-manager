package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
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
                if (idObj instanceof Long id && id != null) ids.add(id);
            } catch (Exception ignored) { /* intentionally ignored */ }
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
        // Find the attribute's edit button and click it
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        // Find the edit button for our attribute - it should be in the row containing attrName
        var editButton = driver.findElement(By.xpath(
            "//tr[.//text()[contains(., '" + attrName + "')]]//button[contains(text(), 'Edytuj')]"
        ));
        editButton.click();

        // Wait for edit form to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        // === 4. Update the attribute name and display order ===
        var nameInput = driver.findElement(By.cssSelector("input[name='name']"));
        nameInput.clear();
        nameInput.sendKeys(updatedName);

        var displayOrderInput = driver.findElement(By.cssSelector("input[name='displayOrder']"));
        displayOrderInput.clear();
        displayOrderInput.sendKeys("99");

        // Submit the edit form
        var submitBtn = driver.findElement(By.cssSelector("form button[type='submit']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);

        // Wait for redirect back to list
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        // === 5. Verify the updated attribute appears on the list with new name ===
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

        var pageSource = driver.getPageSource();
        assert pageSource.contains(updatedName) : "Updated attribute name must be visible on list";
        assert !pageSource.contains(attrName) : "Old attribute name must not be visible on list";

        // === 6. Verify the display order was updated ===
        // The attribute should appear last (displayOrder 99)
        // We can't easily test position, but we can verify the edit persisted by
        // opening the edit form again and checking the value
        driver.get(baseUrl() + "/band/inventory-attributes?type=AWARD");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        var editButton2 = driver.findElement(By.xpath(
            "//tr[.//text()[contains(., '" + updatedName + "')]]//button[contains(text(), 'Edytuj')]"
        ));
        editButton2.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        var nameInput2 = driver.findElement(By.cssSelector("input[name='name']"));
        assert nameInput2.getAttribute("value").equals(updatedName) : "Updated name must persist in edit form";

        var displayOrderInput2 = driver.findElement(By.cssSelector("input[name='displayOrder']"));
        assert displayOrderInput2.getAttribute("value").equals("99") : "Display order must be 99";
    }

    // Helper methods copied from AttributeFlowUiTest
    private void createInventoryAttributeViaUI(String type, String name, String attrType, WebDriverWait wait) {
        driver.get(baseUrl() + "/band/inventory-attributes/new?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

        driver.findElement(By.cssSelector("input[name='name']")).sendKeys(name);
        driver.findElement(By.cssSelector("select[name='attributeType']")).click();
        driver.findElement(By.cssSelector("select[name='attributeType'] option[value='" + attrType + "']")).click();
        driver.findElement(By.cssSelector("input[name='displayOrder']")).clear();
        driver.findElement(By.cssSelector("input[name='displayOrder']")).sendKeys("1");
        driver.findElement(By.cssSelector("input[name='displayInList']")).click();

        try {
            var submitBtn = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("form button[type='submit']")));
            submitBtn.click();
        } catch (org.openqa.selenium.TimeoutException e) {
            var btn = driver.findElement(By.cssSelector("form button[type='submit']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
        }
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));
    }

    private void assertAttributeVisibleOnList(String type, String name, WebDriverWait wait) {
        driver.get(baseUrl() + "/band/inventory-attributes?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
        assert driver.getPageSource().contains(name) : "Attribute '%s' must be visible on %s list".formatted(name, type);
    }
}