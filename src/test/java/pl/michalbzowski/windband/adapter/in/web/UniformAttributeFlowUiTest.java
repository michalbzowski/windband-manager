package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.*;

import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test: full flow for uniform attribute with SELECT type.
 *
 * <p>Scenario:
 * 1. Create SELECT attribute "Rodzaj" for uniforms via API (options: Mundur, Koszula, Czapka; required; displayInList)
 * 2. Create 3 uniform items via API with different attribute values, assign to member
 * 3. Verify on inventory list that attribute column shows correct values
 * 4. Open member detail page → verify all 3 uniform items are visible</p>
 */
class UniformAttributeFlowUiTest extends UiTestBase {

    private static final String[] UNIFORM_OPTIONS = {"Mundur", "Koszula", "Czapka"};
    private static final String ATTR_NAME = "Rodzaj";

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UniformAttributeDefRepository uniformAttrDefRepository;

    @Autowired
    private UniformAttributeValueRepository uniformAttrValueRepository;

    @Test
    void shouldCreateSelectAttributeAddUniformsAndAssignToMember() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String attrName = ATTR_NAME + "-" + unique;
        String firstName = "UniTest" + unique;
        String lastName = "UniLast" + unique;
        String dob = "1990-05-15";

        Band band = bandRepository.findById(1L).orElseThrow();
        // Use band-scoped helper — instruments must belong to band 1 (admin's band) to
        // appear in the member form's instrument dropdown after V28.
        Long instrumentId = createTestBand1Instrument("UniInst" + unique);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        Long memberId = null;
        Long attrDefId = null;

        try {
            // ============================================================
            // STEP 1: Create SELECT attribute "Rodzaj" for uniforms via API
            // ============================================================
            UniformAttributeDef attrDef = UniformAttributeDef.create(
                    band, attrName, "SELECT", true, true, 0,
                    String.join(", ", UNIFORM_OPTIONS));
            attrDef = uniformAttrDefRepository.save(attrDef);
            attrDefId = attrDef.getId();

            // Verify attribute appears on the list page
            loginAndNavigateTo("/band/inventory-attributes?type=UNIFORM");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));
            String listContent = driver.findElement(By.cssSelector("#content")).getText();
            assertThat(listContent)
                    .as("Attribute list should contain the new attribute")
                    .contains(attrName);
            assertThat(listContent)
                    .as("Attribute list should show type SELECT")
                    .contains("Wybór");

            // ============================================================
            // STEP 2: Create a member
            // ============================================================
            memberId = createMemberViaUi(wait, firstName, lastName, dob, instrumentId);
            Member member = memberRepository.findById(memberId).orElseThrow();

            // ============================================================
            // STEP 3: Create 3 uniform items via API with attribute values
            // ============================================================
            Map<String, Long> optionToItemId = new HashMap<>();
            for (String option : UNIFORM_OPTIONS) {
                UniformItem item = inventoryRepository.saveUniformItem(
                        UniformItem.createOwned("Uniform-" + option + "-" + unique, band));

                // Set attribute value
                UniformAttributeValue attrValue = UniformAttributeValue.create(item, attrDef, option);
                uniformAttrValueRepository.save(attrValue);

                // Assign to member
                item.assignTo(member);
                inventoryRepository.saveUniformItem(item);

                optionToItemId.put(option, item.getId());
            }

            // ============================================================
            // STEP 4: Go to inventory → Uniforms tab → verify attribute column
            // ============================================================
            driver.get(baseUrl() + "/inventory");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content")));

            WebElement strojeTab = driver.findElement(By.cssSelector("button[data-tab='uniforms']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", strojeTab);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#uniforms-content")));

            String uniformsContent = driver.findElement(By.cssSelector("#uniforms-content")).getText();

            // Verify attribute column header
            assertThat(uniformsContent)
                    .as("Uniforms list should have attribute column: " + attrName)
                    .contains(attrName);

            // Verify each option value appears in the list
            for (String option : UNIFORM_OPTIONS) {
                assertThat(uniformsContent)
                        .as("Uniforms list should show attribute value: " + option)
                        .contains(option);
            }

            // ============================================================
            // STEP 5: Navigate to member detail page
            // ============================================================
            driver.get(baseUrl() + "/members");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content")));

            String detailXpath = String.format(
                    "//tr[td[contains(., '%s')]]//a[contains(@href, '/members/%d/detail')]",
                    firstName + " " + lastName, memberId);
            WebElement detailLink = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.xpath(detailXpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", detailLink);

            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-detail-content")));

            // ============================================================
            // STEP 6: Verify member detail shows 3 uniform items
            // ============================================================
            String detailContent = driver.findElement(By.cssSelector("#member-detail-content")).getText();

            assertThat(detailContent)
                    .as("Detail page should show member name")
                    .contains(firstName + " " + lastName);

            assertThat(detailContent)
                    .as("Detail page should have uniform items section")
                    .contains("Elementy ekwipunku");

            // Verify each uniform item name appears
            for (String option : UNIFORM_OPTIONS) {
                assertThat(detailContent)
                        .as("Member detail should show uniform: Uniform-" + option)
                        .contains("Uniform-" + option + "-" + unique);
            }

        } finally {
            // ============================================================
            // CLEANUP
            // ============================================================
            cleanupTestData(memberId, attrDefId);
        }
    }

    // === STEP IMPLEMENTATIONS ===

    private Long createMemberViaUi(WebDriverWait wait, String firstName, String lastName, String dob, Long instrumentId) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
        driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
        setDateField("dateOfBirth", dob);

        WebElement instSelect = driver.findElement(By.cssSelector("select[name='instrumentId']"));
        instSelect.click();
        driver.findElement(By.cssSelector("select[name='instrumentId'] option[value='" + instrumentId + "']")).click();

        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#members-content"), firstName + " " + lastName));

        return readMemberIdFromEditButton(wait, firstName + " " + lastName);
    }

    // === CLEANUP ===

    private void cleanupTestData(Long memberId, Long attrDefId) {
        if (memberId != null) {
            Member member = memberRepository.findById(memberId).orElse(null);
            if (member != null) {
                inventoryRepository.findUniformItemsByMember(member).forEach(item -> {
                    item.unassign();
                    uniformAttrValueRepository.findByUniformItem(item).forEach(uniformAttrValueRepository::delete);
                    inventoryRepository.deleteUniformItem(item);
                });
            }
            deleteMemberViaApi(memberId);
        }
        if (attrDefId != null) {
            uniformAttrDefRepository.findById(attrDefId).ifPresent(uniformAttrDefRepository::delete);
        }
    }

    // === HELPERS ===

    private void setDateField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = '" + value + "';", input);
    }

    private Long readMemberIdFromEditButton(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(text(), 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        String hxGet = btn.getAttribute("hx-get");
        if (hxGet == null) {
            throw new IllegalStateException("'Edytuj' button has no hx-get: " + btn.getAttribute("outerHTML"));
        }
        return Long.parseLong(hxGet.split("/")[2]);
    }

    @SuppressWarnings("unchecked")
    private boolean deleteMemberViaApi(Long id) {
        String script = ""
                + "var done = arguments[0];"
                + "fetch('/api/members/" + id + "', {method: 'DELETE', credentials: 'same-origin'})"
                + "  .then(function(r) { done(r.status); })"
                + "  .catch(function(e) { done(0); });";
        Object status = ((JavascriptExecutor) driver).executeAsyncScript(script);
        return status instanceof Number && ((Number) status).intValue() == 204;
    }
}
