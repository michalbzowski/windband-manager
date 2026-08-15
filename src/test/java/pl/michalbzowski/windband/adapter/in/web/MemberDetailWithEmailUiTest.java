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
import java.util.List;
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

    @Test
    void shouldShowConsentGivenWhenMemberHasAllConsentsGranted() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Consent" + unique;
        String lastName = "Test" + unique;
        String dob = "1990-05-15";
        String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + "@test.pl";

        Band band = bandRepository.findById(1L).orElseThrow();
        Long instrumentId = createTestBand1Instrument("ConsentInst" + unique);

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

            // === STEP 2: Grant all three consents (MERGE to handle INSERT or UPDATE) ===
            // MemberWelcomeService creates consent entries with granted=false in an @Async method,
            // which may not have completed yet. Use MERGE to handle both cases (H2 syntax).
            for (String consentType : List.of("EVENTS", "MANAGER_MESSAGES", "INVENTORY_SUMMARY")) {
                jdbcTemplate.update(
                        "MERGE INTO member_consents (member_id, consent_type, granted, granted_at) " +
                        "KEY(member_id, consent_type) VALUES (?, ?, ?, ?)",
                        memberId, consentType, true, java.time.Instant.now());
            }

            // Also update Member.emailConsentGiven to true (normally done by ConsentService.updateConsents())
            // The template uses member.emailConsentGiven which is synced by ConsentService
            // when ANY consent is granted. We need to simulate this behavior in the test.
            jdbcTemplate.update(
                    "UPDATE members SET email_consent_given = ? WHERE id = ?",
                    true, memberId);

            // Verify all consents are granted
            for (String consentType : List.of("EVENTS", "MANAGER_MESSAGES", "INVENTORY_SUMMARY")) {
                Boolean granted = jdbcTemplate.queryForObject(
                        "SELECT granted FROM member_consents WHERE member_id = ? AND consent_type = ?",
                        Boolean.class, memberId, consentType);
                assertThat(granted).as("Consent %s must be granted", consentType).isTrue();
            }

            // Verify emailConsentGiven is also true
            Boolean emailConsentGiven = jdbcTemplate.queryForObject(
                    "SELECT email_consent_given FROM members WHERE id = ?",
                    Boolean.class, memberId);
            assertThat(emailConsentGiven).as("emailConsentGiven must be true when consents granted").isTrue();

            // === STEP 3: Navigate to member detail page ===
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

            // === STEP 4: Verify consent status shows "Wyraził zgodę" ===
            String pageContent = driver.findElement(By.cssSelector("#member-detail-content")).getText();

            assertThat(pageContent)
                    .as("Detail page should show member name")
                    .contains(firstName + " " + lastName);
            assertThat(pageContent)
                    .as("Detail page should show email")
                    .contains(email);
            assertThat(pageContent)
                    .as("Detail page should show consent status (Wyraził zgodę since all granted)")
                    .contains("Wyraził zgodę");
            assertThat(pageContent)
                    .as("Detail page should NOT show 'Brak zgody' when consents granted")
                    .doesNotContain("Brak zgody");
            // The resend button should NOT be visible when emailConsentGiven is true
            assertThat(pageContent)
                    .as("Resend button should not be visible when emailConsentGiven is true")
                    .doesNotContain("Wyślij prośbę o zgodę");

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
