package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test: add member with instrument + date fields, edit dateOfBirth, verify no 500 error toast.
 *
 * <p>Covers the full flow:
 * create → verify pre-population → change dateOfBirth → save → verify no error toast →
 * re-open edit → verify updated dateOfBirth persisted.</p>
 */
class MemberAddEditDateOfBirthUiTest extends UiTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldAddMemberWithInstrumentAndEditDateOfBirth() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "DobTest" + unique;
        String lastName = "DobLast" + unique;
        String initialDob = "1990-05-15";
        String joinedDate = "2020-03-01";
        String updatedDob = "1985-12-25";

        // Create an instrument to select
        Instrument instrument = instrumentRepository.save(Instrument.create("TestInst" + unique));
        Long instrumentId = instrument.getId();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Add a member via UI with all fields including instrument ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(text(), 'Dodaj muzyka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            fillField("firstName", firstName);
            fillField("lastName", lastName);
            fillDateField("dateOfBirth", initialDob);
            fillDateField("joinedDate", joinedDate);

            // Select instrument from dropdown
            WebElement instrumentSelect = driver.findElement(By.cssSelector("select[name='instrumentId']"));
            instrumentSelect.click();
            driver.findElement(By.cssSelector("select[name='instrumentId'] option[value='" + instrumentId + "']")).click();

            submitPrimaryFormButton();

            // === STEP 2: Find the new member on the list and open edit ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);
            assertThat(memberId).as("New member should have an id").isNotNull();

            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // === STEP 3: Verify pre-population — all fields match what we entered ===
            assertThat(driver.findElement(By.cssSelector("input[name='firstName']")).getAttribute("value"))
                    .as("firstName should be pre-populated").isEqualTo(firstName);
            assertThat(driver.findElement(By.cssSelector("input[name='lastName']")).getAttribute("value"))
                    .as("lastName should be pre-populated").isEqualTo(lastName);

            String dobInForm = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(dobInForm)
                    .as("dateOfBirth should be pre-populated with initial value")
                    .isEqualTo(initialDob);

            String joinedInForm = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='joinedDate']\").value;");
            assertThat(joinedInForm)
                    .as("joinedDate should be pre-populated")
                    .isEqualTo(joinedDate);

            String selectedInstrument = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"select[name='instrumentId']\").value;");
            assertThat(selectedInstrument)
                    .as("instrumentId should be pre-populated")
                    .isEqualTo(String.valueOf(instrumentId));

            // === STEP 4: Change only dateOfBirth, remember the new value ===
            clearAndFillDateField("dateOfBirth", updatedDob);
            submitPrimaryFormButton();

            // === STEP 5: Find the member on the list after edit ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            // === STEP 6: Verify no 500 error toast (red toast with "500" text) ===
            // Error toasts are red and contain the status code. They don't auto-hide.
            // Check that no error toast is visible in the toast container.
            String toastContainerText = (String) ((JavascriptExecutor) driver)
                    .executeScript("var tc = document.getElementById('toast-container'); return tc ? tc.textContent : '';");
            assertThat(toastContainerText)
                    .as("No error toast should be visible after saving edited member")
                    .doesNotContain("500");

            // Also check no red toast elements exist
            var errorToasts = driver.findElements(By.cssSelector("#toast-container .toast.error"));
            assertThat(errorToasts)
                    .as("No error (red) toast should be present after save")
                    .isEmpty();

            // === STEP 7: Re-open edit for the same member ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // === STEP 8: Verify dateOfBirth is the updated value ===
            String dobAfterEdit = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(dobAfterEdit)
                    .as("dateOfBirth should have the updated value after edit-save-reopen")
                    .isEqualTo(updatedDob);

        } finally {
            // === Cleanup: delete the test member and instrument ===
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            } else {
                memberRepository.findAllActive().stream()
                        .filter(m -> firstName.equals(m.getFirstName()))
                        .forEach(m -> deleteMemberViaApi(m.getId()));
            }
            instrumentRepository.findById(instrumentId).ifPresent(i -> deleteInstrumentViaApi(i.getId()));
        }
    }

    // --- Helper methods (same patterns as MemberUiTest) ---

    private void fillField(String name, String value) {
        driver.findElement(By.cssSelector("input[name='" + name + "']")).sendKeys(value);
    }

    private void fillDateField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = '" + value + "';", input);
    }

    private void clearAndFillDateField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        input.clear();
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value = '" + value + "';", input);
    }

    private void submitPrimaryFormButton() {
        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();
    }

    private void clickEditForMember(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(text(), 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
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

    @SuppressWarnings("unchecked")
    private boolean deleteInstrumentViaApi(Long id) {
        String script = ""
                + "var done = arguments[0];"
                + "fetch('/api/instruments/" + id + "', {method: 'DELETE', credentials: 'same-origin'})"
                + "  .then(function(r) { done(r.status); })"
                + "  .catch(function(e) { done(0); });";
        Object status = ((JavascriptExecutor) driver).executeAsyncScript(script);
        return status instanceof Number && ((Number) status).intValue() == 204;
    }
}
