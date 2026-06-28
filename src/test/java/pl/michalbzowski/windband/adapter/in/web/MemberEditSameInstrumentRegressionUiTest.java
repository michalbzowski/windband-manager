package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: editing a member's dateOfBirth while keeping the same instrument
 * must NOT cause a duplicate key violation (HTTP 500).
 *
 * <p>Root cause of the original bug: {@code Member.changeInstrument()} always executed
 * {@code instruments.clear() + add(new MemberInstrument(...))} even when the instrument
 * hadn't changed. With {@code orphanRemoval = true}, Hibernate would INSERT the new row
 * before DELETEing the old one, violating the unique constraint
 * {@code member_instruments_member_id_instrument_id_key}.</p>
 *
 * <p>Fix: {@code changeInstrument()} now returns early (no-op) if the member already
 * has the given instrument assigned.</p>
 */
class MemberEditSameInstrumentRegressionUiTest extends UiTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldEditDateOfBirthWithoutChangingInstrument_no500() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "RegrTest" + unique;
        String lastName = "RegrLast" + unique;
        String initialDob = "1990-05-15";
        String updatedDob = "1985-12-25";

        // Create instrument directly in DB
        Instrument instrument = instrumentRepository.save(Instrument.create("RegrInst" + unique));
        Long instrumentId = instrument.getId();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Add a member with instrument ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            fillField("firstName", firstName);
            fillField("lastName", lastName);
            setDateField("dateOfBirth", initialDob);

            // Select instrument
            WebElement instrumentSelect = driver.findElement(By.cssSelector("select[name='instrumentId']"));
            instrumentSelect.click();
            driver.findElement(By.cssSelector("select[name='instrumentId'] option[value='" + instrumentId + "']")).click();

            submitPrimaryFormButton();

            // Wait for list to reload with the new member
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);
            assertThat(memberId).as("New member should have an id").isNotNull();

            // === STEP 2: Open edit form — instrument is pre-selected ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // Verify instrument is pre-selected
            String selectedInstrument = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"select[name='instrumentId']\").value;");
            assertThat(selectedInstrument)
                    .as("Instrument should be pre-selected in edit form")
                    .isEqualTo(String.valueOf(instrumentId));

            // === STEP 3: Change ONLY dateOfBirth — do NOT touch instrument ===
            clearAndSetDateField("dateOfBirth", updatedDob);
            submitPrimaryFormButton();

            // === STEP 4: Verify NO 500 error toast (red toast with "500") ===
            // Wait for the list to reload (success toast "Zapisano członka" appears)
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            // Check toast container for error indicators
            String toastContainerText = (String) ((JavascriptExecutor) driver)
                    .executeScript("var tc = document.getElementById('toast-container'); return tc ? tc.textContent : '';");
            assertThat(toastContainerText)
                    .as("No error toast (500) should be visible after editing member with same instrument")
                    .doesNotContain("500");

            // Verify no red error toasts exist
            var errorToasts = driver.findElements(By.cssSelector("#toast-container .toast.error"));
            assertThat(errorToasts)
                    .as("No error (red) toast should be present after save")
                    .isEmpty();

            // === STEP 5: Re-open edit to verify instrument is still assigned ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            String instrumentAfterEdit = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"select[name='instrumentId']\").value;");
            assertThat(instrumentAfterEdit)
                    .as("Instrument should still be assigned after edit with same instrument")
                    .isEqualTo(String.valueOf(instrumentId));

            // Also verify dateOfBirth was updated
            String dobAfterEdit = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(dobAfterEdit)
                    .as("dateOfBirth should have the updated value")
                    .isEqualTo(updatedDob);

        } finally {
            // Cleanup
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

    // --- Helper methods ---

    private void fillField(String name, String value) {
        driver.findElement(By.cssSelector("input[name='" + name + "']")).sendKeys(value);
    }

    private void setDateField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = '" + value + "';", input);
    }

    private void clearAndSetDateField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        input.clear();
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = '" + value + "';", input);
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
