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
import pl.michalbzowski.windband.domain.member.*;
import pl.michalbzowski.windband.domain.inventory.*;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test: verify the member detail page works when member has email
 */
class MemberDetailWithEmailUiTest extends UiTestBase {

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
    void shouldOpenMemberDetailWithEmailAndShowConsentStatus() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Email" + unique;
        String lastName = "Test" + unique;
        String dob = "1990-05-15";
        String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@test.pl";

        Band band = bandRepository.findById(1L).orElseThrow();
        Long instrumentId = createTestBand1Instrument("DetailInst" + unique);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Add a member WITH EMAIL ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
            driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
            setDateField("dateOfBirth", dob);
            driver.findElement(By.cssSelector("input[name='email']")).sendKeys(email);

            WebElement instrumentSelect = driver.findElement(By.cssSelector("select[name='instrumentId']"));
            instrumentSelect.click();
            driver.findElement(By.cssSelector("select[name='instrumentId'] option[value='" + instrumentId + "']")).click();

            driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);

            // === STEP 2: Navigate to member detail page ===
            driver.get(baseUrl() + "/members");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content")));

            String detailXpath = String.format(
                    "//tr[td[contains(., '%s')]]//a[contains(@href, '/members/%d/detail')]",
                    firstName + " " + lastName, memberId);
            WebElement detailLink = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.xpath(detailXpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", detailLink);

            // Wait for detail page to load
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#member-detail-content")));

            // === STEP 3: Verify basic info is displayed including email and consent status ===
            String pageContent = driver.findElement(By.cssSelector("#member-detail-content")).getText();

            assertThat(pageContent)
                    .as("Detail page should show member name")
                    .contains(firstName + " " + lastName);
            assertThat(pageContent)
                    .as("Detail page should show email")
                    .contains(email);
            assertThat(pageContent)
                    .as("Detail page should show consent status (Brak zgody since not granted)")
                    .contains("Brak zgody");
            assertThat(pageContent)
                    .as("Detail page should show resend button when email but no consent")
                    .contains("Wyślij prośbę o zgodę");

        } finally {
            if (memberId != null) {
                deleteMemberViaApi(memberId);
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
