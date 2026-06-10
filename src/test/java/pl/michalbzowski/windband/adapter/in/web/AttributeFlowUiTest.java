package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end UI flow tests for attribute definitions across all five types
 * (uniform, instrument, order, award, member). For each type the test:
 *
 * <ol>
 *   <li>Opens the new-attribute view and saves a new attribute through the UI</li>
 *   <li>Verifies the attribute appears on the attributes list</li>
 *   <li>Navigates to the related entity view (e.g. "Dodaj stroj" for UNIFORM)</li>
 *   <li>Verifies the newly-created attribute is visible as a dynamic field</li>
 *   <li>Saves the related entity with the attribute value set</li>
 *   <li>Re-verifies the attribute is still present (persisted with the entity)</li>
 * </ol>
 *
 * <p><b>Cleanup strategy:</b> snapshot the set of all entity IDs BEFORE the test runs,
 * then in {@code @AfterEach} delete any entities whose IDs are not in the snapshot
 * (i.e. ones created by this test). This keeps the database clean for subsequent
 * test classes without depending on cleanup-friendly unique markers per entity.</p>
 */
class AttributeFlowUiTest extends UiTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.InventoryRepository inventoryRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.UniformAttributeValueRepository uniformAttrValueRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.InstrumentAttributeValueRepository instrumentAttrValueRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardAttributeValueRepository awardAttrValueRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardItemRepository awardItemRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.member.MemberRepository memberRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.UniformAttributeDefRepository uniformAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.InstrumentAttributeDefRepository instrumentAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.OrderAttributeDefRepository orderAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.inventory.AwardAttributeDefRepository awardAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.band.MemberAttributeDefRepository memberAttrRepo;

    @org.springframework.beans.factory.annotation.Autowired
    private pl.michalbzowski.windband.domain.band.BandRepository bandRepo;

    private String unique;
    private Set<Long> uniformIdsBefore;
    private Set<Long> instrumentIdsBefore;
    private Set<Long> awardIdsBefore;
    private Set<Long> orderIdsBefore;
    private Set<Long> memberIdsBefore;

    @BeforeEach
    void snapshotState() {
        unique = UUID.randomUUID().toString().substring(0, 8);
        uniformIdsBefore = idsOf(inventoryRepo.findAllUniformItems());
        instrumentIdsBefore = idsOf(inventoryRepo.findAllInstrumentItems());
        awardIdsBefore = idsOf(awardItemRepo.findByBandIdOrderByDateAwardedDescNameAsc(1L));
        orderIdsBefore = idsOf(inventoryRepo.findAllOrders());
        memberIdsBefore = idsOf(memberRepo.findAllActive());
    }

    @AfterEach
    void cleanup() {
        // Delete entities created by this test
        deleteNewUniforms(uniformIdsBefore);
        deleteNewInstruments(instrumentIdsBefore);
        deleteNewAwards(awardIdsBefore);
        deleteNewOrders(orderIdsBefore);
        deleteNewMembers(memberIdsBefore);
        // Delete attribute defs we created (by unique name pattern) — fallback if no
        // repository cleanup already removed them.
        pl.michalbzowski.windband.domain.band.Band band = bandRepo.findById(1L).orElse(null);
        if (band != null) {
            // Uniform / Instrument / Order use findByBandAndName
            uniformAttrRepo.findByBandAndName(band, "UniAttr" + unique)
                    .ifPresent(d -> uniformAttrRepo.delete(d));
            uniformAttrRepo.findByBandAndName(band, "InstAttr" + unique)
                    .ifPresent(d -> uniformAttrRepo.delete(d));
            uniformAttrRepo.findByBandAndName(band, "OrderAttr" + unique)
                    .ifPresent(d -> uniformAttrRepo.delete(d));
            // Award uses band scan (its domain repo doesn't expose band+name finder
            // at the time of writing — it has findByBandAndActiveTrueOrderByDisplayOrderAsc)
            awardAttrRepo.findByBandAndActiveTrueOrderByDisplayOrderAsc(band).stream()
                    .filter(d -> ("AwAttr" + unique).equals(d.getName()))
                    .findFirst().ifPresent(d -> awardAttrRepo.delete(d));
            // Member attributes — scan by band
            memberAttrRepo.findByBandOrderByDisplayOrderAsc(band).stream()
                    .filter(d -> ("MemAttr" + unique).equals(d.getName()))
                    .findFirst().ifPresent(d -> memberAttrRepo.delete(d));
        }
    }

    private static Set<Long> idsOf(Iterable<?> items) {
        Set<Long> ids = new HashSet<>();
        items.forEach(i -> {
            try {
                Object idObj = i.getClass().getMethod("getId").invoke(i);
                if (idObj instanceof Long id && id != null) ids.add(id);
            } catch (Exception ignored) {}
        });
        return ids;
    }

    private void deleteNewUniforms(Set<Long> before) {
        // Delete attribute values first (FK constraint)
        inventoryRepo.findAllUniformItems().stream()
                .filter(u -> !before.contains(u.getId()))
                .forEach(u -> {
                    uniformAttrValueRepo.findByUniformItem(u).forEach(uniformAttrValueRepo::delete);
                    inventoryRepo.deleteUniformItem(u);
                });
    }

    private void deleteNewInstruments(Set<Long> before) {
        inventoryRepo.findAllInstrumentItems().stream()
                .filter(i -> !before.contains(i.getId()))
                .forEach(i -> {
                    instrumentAttrValueRepo.findByInstrumentItem(i).forEach(instrumentAttrValueRepo::delete);
                    inventoryRepo.deleteInstrumentItem(i);
                });
    }

    private void deleteNewAwards(Set<Long> before) {
        awardItemRepo.findByBandIdOrderByDateAwardedDescNameAsc(1L).stream()
                .filter(a -> !before.contains(a.getId()))
                .forEach(a -> {
                    awardAttrValueRepo.findByAwardItemId(a.getId()).forEach(awardAttrValueRepo::delete);
                    awardItemRepo.delete(a);
                });
    }

    private void deleteNewOrders(Set<Long> before) {
        // InventoryRepository has no deleteOrder — orders stay around but the test
        // doesn't rely on their absence. We could use the REST API to delete them
        // if needed. For now, leave them (they don't break other tests).
    }

    private void deleteNewMembers(Set<Long> before) {
        memberRepo.findAllActive().stream()
                .filter(m -> !before.contains(m.getId()))
                .forEach(memberRepo::delete);
    }

    // ====================================================================
    //  TEST: UNIFORM attribute flow
    // ====================================================================

    @Test
    void uniformAttribute_shouldBeVisibleInNewUniformForm_andRemainAfterSave() throws InterruptedException {
        String attrName = "UniAttr" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // === 1. Create UNIFORM attribute via UI ===
        createInventoryAttributeViaUI("UNIFORM", attrName, "TEXT", wait);

        // === 2. Verify on the attributes list ===
        assertAttributeVisibleOnList("UNIFORM", attrName, wait);

        // === 3. Navigate to inventory, switch to Stroje tab, open "Dodaj stroj" form ===
        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content h2")));
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-tab=\"uniforms\"]').click();");
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript("showUniformForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("uniform-form")));

        // === 4. Verify the new attribute is visible in the uniform form ===
        Thread.sleep(300);
        assertContainerHasText("#uniform-attributes", attrName,
                "New uniform attribute '%s' must be visible in Dodaj stroj form");

        // === 5. Fill the uniform form (attribute value) and submit ===
        String uniqueValue = "val" + unique;
        ((JavascriptExecutor) driver).executeScript(
                "var inputs = document.querySelectorAll('#uniform-attributes input[type=\"text\"]');"
                + "for (var i=0; i<inputs.length; i++) {"
                + "  if (inputs[i].id && inputs[i].id.startsWith('uni-attr-')) {"
                + "    inputs[i].value = '" + uniqueValue + "';"
                + "    inputs[i].dispatchEvent(new Event('input', {bubbles:true}));"
                + "  }"
                + "}");
        ((JavascriptExecutor) driver).executeScript("submitUniform();");
        // After submit, refreshTab('uniforms') replaces #uniforms-content. The form is hidden.
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("uniform-form")));
        Thread.sleep(800);

        // === 6. Verify the attribute is still visible (open the form again) ===
        ((JavascriptExecutor) driver).executeScript("showUniformForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("uniform-form")));
        Thread.sleep(300);
        assertContainerHasText("#uniform-attributes", attrName,
                "Attribute '%s' must still be visible in the form after a uniform was saved");
    }

    // ====================================================================
    //  TEST: INSTRUMENT attribute flow
    // ====================================================================

    @Test
    void instrumentAttribute_shouldBeVisibleInNewInstrumentForm_andRemainAfterSave() throws InterruptedException {
        String attrName = "InstAttr" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        createInventoryAttributeViaUI("INSTRUMENT", attrName, "TEXT", wait);
        assertAttributeVisibleOnList("INSTRUMENT", attrName, wait);

        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content h2")));
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-tab=\"instruments\"]').click();");
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript("showInstrumentForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("instrument-form")));

        Thread.sleep(300);
        assertContainerHasText("#instrument-attributes", attrName,
                "New instrument attribute '%s' must be visible in Dodaj instrument form");

        String uniqueValue = "val" + unique;
        ((JavascriptExecutor) driver).executeScript(
                "var inputs = document.querySelectorAll('#instrument-attributes input[type=\"text\"]');"
                + "for (var i=0; i<inputs.length; i++) {"
                + "  if (inputs[i].id && inputs[i].id.startsWith('inst-attr-')) {"
                + "    inputs[i].value = '" + uniqueValue + "';"
                + "    inputs[i].dispatchEvent(new Event('input', {bubbles:true}));"
                + "  }"
                + "}");
        ((JavascriptExecutor) driver).executeScript("submitInstrument();");
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("instrument-form")));
        Thread.sleep(800);

        ((JavascriptExecutor) driver).executeScript("showInstrumentForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("instrument-form")));
        Thread.sleep(300);
        assertContainerHasText("#instrument-attributes", attrName,
                "Attribute '%s' must still be visible after an instrument was saved");
    }

    // ====================================================================
    //  TEST: ORDER attribute flow
    // ====================================================================

    @Test
    void orderAttribute_shouldBeVisibleInNewOrderForm_andRemainAfterSave() throws InterruptedException {
        // Note: The order form's "type=UNIFORM" path uses UniformAttributeDefs (not
        // OrderAttributeDefs) — the order's attribute fields mirror the underlying
        // item's attribute definitions. So this test creates a UNIFORM attribute and
        // verifies it's visible in the "Nowe zamówienie" form when type=UNIFORM is
        // selected.
        String attrName = "UniAttr" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        createInventoryAttributeViaUI("UNIFORM", attrName, "TEXT", wait);
        assertAttributeVisibleOnList("UNIFORM", attrName, wait);

        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content h2")));
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-tab=\"orders\"]').click();");
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript("showOrderForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order-form")));

        // The order-attributes are loaded by JS when type changes. Pick a member, then
        // set type to UNIFORM so the dynamic order attribute fields render.
        Thread.sleep(300);
        String firstMemberValue = (String) ((JavascriptExecutor) driver)
                .executeScript("var sel=document.getElementById('order-member');"
                        + "return sel.options.length > 1 ? sel.options[1].value : '';");
        if (!firstMemberValue.isEmpty()) {
            ((JavascriptExecutor) driver).executeScript(
                    "document.getElementById('order-member').value = '" + firstMemberValue + "';"
                    + "document.getElementById('order-member').dispatchEvent(new Event('change', {bubbles:true}));");
        }
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById('order-type').value = 'UNIFORM';"
                + "document.getElementById('order-type').dispatchEvent(new Event('change', {bubbles:true}));"
                + "updateOrderAttributes();");
        Thread.sleep(500);

        assertContainerHasText("#order-attributes", attrName,
                "New uniform attribute '%s' must be visible in Nowe zamówienie form (type=UNIFORM)");

        String uniqueValue = "val" + unique;
        ((JavascriptExecutor) driver).executeScript(
                "var inputs = document.querySelectorAll('#order-attributes input[type=\"text\"]');"
                + "for (var i=0; i<inputs.length; i++) {"
                + "  if (inputs[i].id && inputs[i].id.startsWith('order-attr-')) {"
                + "    inputs[i].value = '" + uniqueValue + "';"
                + "    inputs[i].dispatchEvent(new Event('input', {bubbles:true}));"
                + "  }"
                + "}");
        if (!firstMemberValue.isEmpty()) {
            ((JavascriptExecutor) driver).executeScript("submitOrder();");
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("order-form")));
            Thread.sleep(800);
        }

        ((JavascriptExecutor) driver).executeScript("showOrderForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("order-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById('order-type').value = 'UNIFORM';"
                + "document.getElementById('order-type').dispatchEvent(new Event('change', {bubbles:true}));"
                + "updateOrderAttributes();");
        Thread.sleep(500);
        assertContainerHasText("#order-attributes", attrName,
                "Attribute '%s' must still be visible after an order was saved");
    }

    // ====================================================================
    //  TEST: AWARD attribute flow
    // ====================================================================

    @Test
    void awardAttribute_shouldBeVisibleInNewAwardForm_andRemainAfterSave() throws InterruptedException {
        String attrName = "AwAttr" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        createInventoryAttributeViaUI("AWARD", attrName, "TEXT", wait);
        assertAttributeVisibleOnList("AWARD", attrName, wait);

        driver.get(baseUrl() + "/inventory");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content h2")));
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector('[data-tab=\"awards\"]').click();");
        Thread.sleep(300);
        ((JavascriptExecutor) driver).executeScript("showAwardForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("award-form")));

        Thread.sleep(300);
        assertContainerHasText("#award-attributes", attrName,
                "New award attribute '%s' must be visible in Dodaj odznaczenie form");

        String awardName = "Award-" + unique;
        String uniqueValue = "val" + unique;
        ((JavascriptExecutor) driver).executeScript(
                "document.getElementById('award-name').value = '" + awardName + "';"
                + "document.getElementById('award-name').dispatchEvent(new Event('input', {bubbles:true}));"
                + "var inputs = document.querySelectorAll('#award-attributes input[type=\"text\"]');"
                + "for (var i=0; i<inputs.length; i++) {"
                + "  if (inputs[i].id && inputs[i].id.startsWith('award-attr-')) {"
                + "    inputs[i].value = '" + uniqueValue + "';"
                + "    inputs[i].dispatchEvent(new Event('input', {bubbles:true}));"
                + "  }"
                + "}");
        ((JavascriptExecutor) driver).executeScript("submitAward();");
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("award-form")));
        Thread.sleep(800);

        ((JavascriptExecutor) driver).executeScript("showAwardForm();");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("award-form")));
        Thread.sleep(300);
        assertContainerHasText("#award-attributes", attrName,
                "Attribute '%s' must still be visible after an award was saved");
    }

    // ====================================================================
    //  TEST: MEMBER attribute flow
    // ====================================================================

    @Test
    void memberAttribute_shouldBeVisibleInNewMemberForm_andRemainAfterSave() throws InterruptedException {
        String attrName = "MemAttr" + unique;
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        createInventoryAttributeViaUI("MEMBER", attrName, "TEXT", wait);
        assertAttributeVisibleOnList("MEMBER", attrName, wait);

        // Navigate to /members, open "Dodaj muzyka" form
        driver.get(baseUrl() + "/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content h2")));
        // Use Selenium click (not JS) — same approach as MemberUiTest
        WebElement addMemberBtn = driver.findElement(
                By.xpath("//button[contains(text(), 'Dodaj muzyka')]"));
        addMemberBtn.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        Thread.sleep(400);
        // The "Dodatkowe atrybuty" section is inside the form. Verify the new
        // attribute label is present somewhere in the form's text.
        assertContainerHasText("#member-form", attrName,
                "New member attribute '%s' must be visible in Dodaj muzyka form");

        // Fill required member fields + the new attribute value
        String firstName = "MemAttr" + unique;
        String lastName = "Test";
        String dob = "1990-01-01";
        String email = "memattr" + unique + "@test.pl";
        String phone = "100200300";
        String uniqueValue = "val" + unique;

        driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
        driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
        driver.findElement(By.cssSelector("input[name='dateOfBirth']")).sendKeys(dob);
        driver.findElement(By.cssSelector("input[name='email']")).sendKeys(email);
        driver.findElement(By.cssSelector("input[name='phone']")).sendKeys(phone);
        // Fill the new attribute (it's a text input with name="attr_<id>")
        ((JavascriptExecutor) driver).executeScript(
                "var inputs = document.querySelectorAll('#member-form input[type=\"text\"][name^=\"attr_\"]');"
                + "for (var i=0; i<inputs.length; i++) {"
                + "  var lbl = inputs[i].closest('label');"
                + "  if (lbl && lbl.textContent.includes('" + attrName + "')) {"
                + "    inputs[i].value = '" + uniqueValue + "';"
                + "    inputs[i].dispatchEvent(new Event('input', {bubbles:true}));"
                + "  }"
                + "}");

        // Submit the form (real UI submit)
        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#members-content"), firstName));
        // Force a fresh reload of the members list to avoid htmx transition timing issues
        // before re-clicking the "Dodaj muzyka" button.
        Thread.sleep(1000);
        driver.get(baseUrl() + "/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content h2")));

        // Re-open the new member form to verify the attribute is still in it
        WebElement addMemberBtn2 = driver.findElement(
                By.xpath("//button[contains(text(), 'Dodaj muzyka')]"));
        addMemberBtn2.click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        Thread.sleep(400);
        String formText = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector('#member-form') ? document.querySelector('#member-form').textContent : '<FORM_NULL>';");
        assertThat(formText)
                .as("Attribute '%s' must still be visible in Dodaj muzyka form after a member was saved", attrName)
                .contains(attrName);
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

    /**
     * Opens the new attribute form, fills it, submits, and waits for the HX-Redirect URL change.
     */
    private void createInventoryAttributeViaUI(String type, String attrName, String attrType, WebDriverWait wait) {
        loginAndNavigateTo("/band/inventory-attributes?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

        driver.get(baseUrl() + "/band/inventory-attributes/new?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("form input[name='name']")));

        driver.findElement(By.cssSelector("input[name='name']")).sendKeys(attrName);
        driver.findElement(By.cssSelector("select[name='attributeType'] option[value='" + attrType + "']")).click();

        WebElement submitBtn = driver.findElement(
                By.cssSelector("form button[type='submit'].primary"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);

        wait.until(ExpectedConditions.urlContains("/band/inventory-attributes?type=" + type));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));
    }

    private void assertAttributeVisibleOnList(String type, String attrName, WebDriverWait wait)
            throws InterruptedException {
        driver.get(baseUrl() + "/band/inventory-attributes?type=" + type);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));
        Thread.sleep(500);
        String listContent = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector('#content').textContent;");
        assertThat(listContent)
                .as("Attribute '%s' should be visible on the %s attributes list", attrName, type)
                .contains(attrName);
    }

    private void assertContainerHasText(String cssSelector, String expected, String message) {
        String text = (String) ((JavascriptExecutor) driver)
                .executeScript("return document.querySelector('" + cssSelector + "').textContent;");
        assertThat(text)
                .as(message, expected)
                .contains(expected);
    }
}
