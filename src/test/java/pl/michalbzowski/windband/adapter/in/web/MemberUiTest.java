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
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for the members page — full add→edit flow through the actual UI.
 *
 * <p>Regression coverage for the form submit pipeline: opening the new form,
 * filling required fields, submitting, and verifying the list re-renders with
 * the new row. Then opening the edit form for that row, modifying ONE field at
 * a time, and verifying each change appears on the list while the previously
 * edited fields are preserved.</p>
 */
class MemberUiTest extends UiTestBase {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Test
    void shouldNavigateToMembersAndOpenNewForm() {
        loginAndNavigateTo("/members");

        assertThat(driver.getTitle()).contains("Członkowie");

        var addButton = driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        var formHeading = driver.findElement(By.cssSelector("#members-content h2"));
        assertThat(formHeading.getText()).contains("Dodaj członka");
    }

    /**
     * Full add → edit flow through the actual UI:
     * 1. Open the new-member form (verifies form opens)
     * 2. Fill required fields, click submit
     * 3. Verify the new member appears on the list with all entered data
     * 4. Click "Edytuj" on that row, change EMAIL only, submit
     * 5. Verify the new email appears, old email is gone, phone preserved
     * 6. Click "Edytuj" again, change PHONE only, submit
     * 7. Verify the new phone appears, email from step 4 is preserved
     */
    @Test
    void shouldAddMemberAndEditOneFieldAtATime() {
        // Unique names so we can identify our member in a possibly-populated list
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "TestFirst" + unique;
        String lastName = "TestLast" + unique;
        String dob = "1990-05-15";
        String initialEmail = firstName.toLowerCase() + "@test.pl";
        String updatedEmail = firstName.toLowerCase() + ".new@test.pl";
        String initialPhone = "111222333";
        String updatedPhone = "999888777";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Open the new-member form via UI ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
            assertThat(driver.findElement(By.cssSelector("#members-content h2")).getText())
                    .contains("Dodaj członka");

            // === STEP 2: Fill the form and submit ===
            fillField("firstName", firstName);
            fillField("lastName", lastName);
            fillField("dateOfBirth", dob);
            fillField("email", initialEmail);
            fillField("phone", initialPhone);
            submitPrimaryFormButton();

            // === STEP 3: Verify the new member appears on the list ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            String listAfterAdd = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(listAfterAdd)
                    .as("After add: member should be visible on the list")
                    .contains(firstName)
                    .contains(lastName)
                    .contains(initialEmail)
                    .contains(initialPhone);

            // Find the new member's id by reading the "Edytuj" button's hx-get attribute
            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);
            assertThat(memberId).as("New member should have an id").isNotNull();

            // === STEP 4: Click "Edytuj", change EMAIL only, submit ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // Verify the form was pre-populated with current values
            String currentEmailBeforeEdit = driver.findElement(
                    By.cssSelector("input[name='email']")).getAttribute("value");
            assertThat(currentEmailBeforeEdit).isEqualTo(initialEmail);

            clearAndFillField("email", updatedEmail);
            submitPrimaryFormButton();

            // === STEP 5: Verify the new email appears, old email gone, phone preserved ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), updatedEmail));

            String listAfterEmailEdit = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(listAfterEmailEdit)
                    .as("After email edit: new email appears, old email gone, phone preserved")
                    .contains(updatedEmail)
                    .doesNotContain(initialEmail)
                    .contains(initialPhone);

            // === STEP 6: Click "Edytuj" again, change PHONE only, submit ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // Verify email from step 4 is still in the form (one-field-at-a-time edit didn't clobber it)
            String emailInForm = driver.findElement(
                    By.cssSelector("input[name='email']")).getAttribute("value");
            assertThat(emailInForm)
                    .as("Edit form should show the previously-edited email, not the original")
                    .isEqualTo(updatedEmail);

            clearAndFillField("phone", updatedPhone);
            submitPrimaryFormButton();

            // === STEP 7: Verify the new phone appears, email from step 4 preserved ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), updatedPhone));

            String listAfterPhoneEdit = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(listAfterPhoneEdit)
                    .as("After phone edit: new phone appears, email from previous edit preserved")
                    .contains(updatedPhone)
                    .contains(updatedEmail)
                    .doesNotContain(initialPhone);
        } finally {
            // === Cleanup: delete the test member so it doesn't pollute the list for other tests ===
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            } else {
                memberRepository.findAllActive().stream()
                        .filter(m -> firstName.equals(m.getFirstName()))
                        .forEach(m -> deleteMemberViaApi(m.getId()));
            }
        }
    }

    /**
     * Full add → edit flow that changes ALL editable fields at once:
     * 1. Add a member with known values
     * 2. Open edit form, verify pre-population
     * 3. Change EVERY field (name, dob, joinedDate, email, phone) at once
     * 4. Submit and verify all new values appear on the list
     * 5. Re-open edit form to verify all fields persisted
     */
    @Test
    void shouldAddMemberAndEditAllFieldsAtOnce() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        // Initial values
        String firstName = "FullEdit" + unique;
        String lastName = "Test" + unique;
        String dob = "1990-01-15";
        String joinedDate = "2020-03-01";
        String email = "fulledit" + unique + "@test.pl";
        String phone = "111222333";
        // Updated values — all different
        String updFirstName = "UpdatedFirst" + unique;
        String updLastName = "UpdatedLast" + unique;
        String updDob = "1985-12-25";
        String updJoinedDate = "2019-06-15";
        String updEmail = "updated" + unique + "@test.pl";
        String updPhone = "999888777";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Add a member via UI ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            fillField("firstName", firstName);
            fillField("lastName", lastName);
            fillField("dateOfBirth", dob);
            fillField("joinedDate", joinedDate);
            fillField("email", email);
            fillField("phone", phone);
            submitPrimaryFormButton();

            // Wait for the list to reload with the new member
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            String listAfterAdd = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(listAfterAdd)
                    .as("After add: member should be visible with initial data")
                    .contains(firstName)
                    .contains(lastName)
                    .contains(email)
                    .contains(phone);

            // Read the new member's id
            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);

            // === STEP 2: Open edit form, verify pre-population ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            assertThat(driver.findElement(By.cssSelector("#members-content h2")).getText())
                    .as("Edit form heading should show 'Edytuj'")
                    .contains("Edytuj członka");

            // Verify all fields are pre-populated with the original values
            assertThat(driver.findElement(By.cssSelector("input[name='firstName']")).getAttribute("value"))
                    .as("firstName should be pre-populated").isEqualTo(firstName);
            assertThat(driver.findElement(By.cssSelector("input[name='lastName']")).getAttribute("value"))
                    .as("lastName should be pre-populated").isEqualTo(lastName);
            String dobValue = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(dobValue)
                    .as("dateOfBirth should be pre-populated").isEqualTo(dob);
            assertThat(driver.findElement(By.cssSelector("input[name='email']")).getAttribute("value"))
                    .as("email should be pre-populated").isEqualTo(email);
            assertThat(driver.findElement(By.cssSelector("input[name='phone']")).getAttribute("value"))
                    .as("phone should be pre-populated").isEqualTo(phone);

            // === STEP 3: Change ALL fields at once ===
            clearAndFillField("firstName", updFirstName);
            clearAndFillField("lastName", updLastName);
            clearAndFillField("dateOfBirth", updDob);
            clearAndFillField("joinedDate", updJoinedDate);
            clearAndFillField("email", updEmail);
            clearAndFillField("phone", updPhone);
            submitPrimaryFormButton();

            // === STEP 4: Verify all new values appear on the list ===
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), updFirstName + " " + updLastName));

            String listAfterEdit = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(listAfterEdit)
                    .as("After full edit: all new values should be visible, old values gone")
                    .contains(updFirstName)
                    .contains(updLastName)
                    .contains(updEmail)
                    .contains(updPhone)
                    .doesNotContain(firstName)
                    .doesNotContain(lastName)
                    .doesNotContain(email)
                    .doesNotContain(phone);

            // === STEP 5: Re-open edit form to verify all fields persisted ===
            clickEditForMember(wait, updFirstName + " " + updLastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            assertThat(driver.findElement(By.cssSelector("input[name='firstName']")).getAttribute("value"))
                    .as("firstName should have updated value").isEqualTo(updFirstName);
            assertThat(driver.findElement(By.cssSelector("input[name='lastName']")).getAttribute("value"))
                    .as("lastName should have updated value").isEqualTo(updLastName);
            String updDobValue = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(updDobValue)
                    .as("dateOfBirth should have updated value").isEqualTo(updDob);
            assertThat(driver.findElement(By.cssSelector("input[name='email']")).getAttribute("value"))
                    .as("email should have updated value").isEqualTo(updEmail);
            assertThat(driver.findElement(By.cssSelector("input[name='phone']")).getAttribute("value"))
                    .as("phone should have updated value").isEqualTo(updPhone);

        } finally {
            // Cleanup
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            } else {
                memberRepository.findAllActive().stream()
                        .filter(m -> updFirstName.equals(m.getFirstName()))
                        .forEach(m -> deleteMemberViaApi(m.getId()));
            }
        }
    }

    private void fillField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        if ("date".equals(input.getAttribute("type"))) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = '" + value + "';", input);
        } else {
            input.sendKeys(value);
        }
    }

    private void clearAndFillField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        input.clear();
        if ("date".equals(input.getAttribute("type"))) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = '" + value + "';", input);
        } else {
            input.sendKeys(value);
        }
    }

    private void submitPrimaryFormButton() {
        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();
    }

    /**
     * Clicks the "Edytuj" button on the row whose first cell contains the given full name.
     * Uses JS click to bypass the htmx transition overlay (same workaround as AttributeUiTest).
     */
    private void clickEditForMember(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(., 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
    }

    /**
     * Reads the new member's id from its "Edytuj" button's hx-get attribute.
     * The button looks like: {@code hx-get="/members/123/edit"}.
     */
    private Long readMemberIdFromEditButton(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(., 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        String hxGet = btn.getAttribute("hx-get");
        // hx-get="/members/{id}/edit"
        if (hxGet == null) {
            throw new IllegalStateException("'Edytuj' button has no hx-get attribute: " + btn.getAttribute("outerHTML"));
        }
        String[] parts = hxGet.split("/");
        // ["", "members", "{id}", "edit"]
        return Long.parseLong(parts[2]);
    }

    /**
     * Performs a DELETE via fetch from the browser context (so the session
     * cookie is included). Returns true if the delete succeeded.
     */
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
