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
import pl.michalbzowski.windband.domain.member.*;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test: verify the member detail page (readonly view) shows basic info
 * and assigned inventory items.
 */
class MemberDetailUiTest extends UiTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private AwardItemRepository awardItemRepository;

    @Test
    void shouldOpenMemberDetailAndShowBasicInfoAndInventory() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Detail" + unique;
        String lastName = "Test" + unique;
        String dob = "1990-05-15";

        Band band = bandRepository.findById(1L).orElseThrow();
        Instrument instrument = instrumentRepository.save(Instrument.create("DetailInst" + unique));

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;
        Long uniformItemId = null;
        Long instrumentItemId = null;
        Long awardItemId = null;

        try {
            // === STEP 1: Add a member with instrument ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
            driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
            setDateField("dateOfBirth", dob);

            WebElement instrumentSelect = driver.findElement(By.cssSelector("select[name='instrumentId']"));
            instrumentSelect.click();
            driver.findElement(By.cssSelector("select[name='instrumentId'] option[value='" + instrument.getId() + "']")).click();

            driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);

            // === STEP 2: Assign inventory items to the member ===
            Member member = memberRepository.findById(memberId).orElseThrow();

            UniformItem uniformItem = inventoryRepository.saveUniformItem(
                    UniformItem.createOwned("Uniform-" + unique, band));
            uniformItem.assignTo(member);
            inventoryRepository.saveUniformItem(uniformItem);
            uniformItemId = uniformItem.getId();

            InstrumentItem instrItem = inventoryRepository.saveInstrumentItem(
                    InstrumentItem.createOwned("Instr-" + unique, band));
            instrItem.assignTo(member);
            inventoryRepository.saveInstrumentItem(instrItem);
            instrumentItemId = instrItem.getId();

            AwardItem awardItem = awardItemRepository.save(AwardItem.create("Award-" + unique, band));
            awardItem.assignTo(member);
            awardItemRepository.save(awardItem);
            awardItemId = awardItem.getId();

            // === STEP 3: Navigate to member detail page via member name link ===
            // Reload the list page to see the new member with detail link
            driver.get(baseUrl() + "/members");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content")));

            // Find the detail link for our member (the member name is now a link)
            String detailXpath = String.format(
                    "//tr[td[contains(., '%s')]]//a[contains(@href, '/members/%d/detail')]",
                    firstName + " " + lastName, memberId);
            WebElement detailLink = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.xpath(detailXpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", detailLink);

            // Wait for detail page to load
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#member-detail-content")));

            // === STEP 4: Verify basic info is displayed ===
            String pageContent = driver.findElement(By.cssSelector("#member-detail-content")).getText();

            assertThat(pageContent)
                    .as("Detail page should show member name")
                    .contains(firstName + " " + lastName);
            assertThat(pageContent)
                    .as("Detail page should show date of birth")
                    .contains("15-05-1990");
            assertThat(pageContent)
                    .as("Detail page should show instrument")
                    .contains("DetailInst" + unique);

            // === STEP 5: Verify assigned inventory items are shown ===
            assertThat(pageContent)
                    .as("Detail page should show assigned uniform item")
                    .contains("Uniform-" + unique);
            assertThat(pageContent)
                    .as("Detail page should show assigned instrument item")
                    .contains("Instr-" + unique);
            assertThat(pageContent)
                    .as("Detail page should show assigned award item")
                    .contains("Award-" + unique);

            // === STEP 6: Verify "Edytuj" button exists ===
            WebElement editBtn = driver.findElement(
                    By.xpath("//div[@id='member-detail-content']//button[contains(text(), 'Edytuj')]"));
            assertThat(editBtn).isNotNull();

        } finally {
            // Cleanup
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            }
            if (uniformItemId != null) {
                inventoryRepository.findUniformItemById(uniformItemId)
                        .ifPresent(i -> {
                            i.unassign();
                            inventoryRepository.deleteUniformItem(i);
                        });
            }
            if (instrumentItemId != null) {
                inventoryRepository.findInstrumentItemById(instrumentItemId)
                        .ifPresent(i -> {
                            i.unassign();
                            inventoryRepository.deleteInstrumentItem(i);
                        });
            }
            if (awardItemId != null) {
                awardItemRepository.findById(awardItemId).ifPresent(awardItemRepository::delete);
            }
        }
    }

    // --- Helpers ---

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
