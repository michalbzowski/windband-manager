package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test reproducing issue #92: editing a member's dateOfBirth causes HTTP 500.
 *
 * <p>Scenario: add a member, then edit the member changing only the date of birth,
 * click save, and verify the member list reloads without errors.</p>
 */
class MemberEditDateOfBirthUiTest extends UiTestBase {

    @Test
    void shouldEditMemberDateOfBirth_without500Error() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "EditDob" + unique;
        String lastName = "Test" + unique;
        String dob = "1990-01-15";
        String updatedDob = "1995-06-20";
        String email = "editdob" + unique + "@test.pl";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Add a member via UI ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
            driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
            driver.findElement(By.cssSelector("input[name='dateOfBirth']")).sendKeys(dob);
            driver.findElement(By.cssSelector("input[name='email']")).sendKeys(email);
            driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

            // Wait for the list to reload with the new member
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            // Read the new member's id
            String xpath = String.format(
                    "//tr[td[contains(., '%s')]]//button[contains(., 'Edytuj')]",
                    firstName + " " + lastName);
            WebElement editBtn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
            String hxGet = editBtn.getAttribute("hx-get");
            memberId = Long.parseLong(hxGet.split("/")[2]);

            // === STEP 2: Open edit form for this member ===
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // Verify the edit form heading
            assertThat(driver.findElement(By.cssSelector("#members-content h2")).getText())
                    .contains("Edytuj członka");

            // === STEP 3: Change only the date of birth ===
            // Use JS to set date value reliably (sendKeys on date inputs can be garbled)
            ((JavascriptExecutor) driver).executeScript(
                    "document.querySelector(\"input[name='dateOfBirth']\").value = '" + updatedDob + "';");

            // === STEP 4: Click save ===
            driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

            // === STEP 5: Verify we land on the member list without 500 error ===
            // The list should reload and contain the member's name
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            // Verify no error toast appeared (no "✕" error indicator)
            String pageText = driver.findElement(By.cssSelector("#members-content")).getText();
            assertThat(pageText)
                    .as("After saving edited member, the list should reload without errors")
                    .contains(firstName);

            // Verify the updated dob is reflected by checking the page source
            WebElement editBtn2 = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn2);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            String dobValue = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector(\"input[name='dateOfBirth']\").value;");
            assertThat(dobValue)
                    .as("Date of birth should be updated to the new value")
                    .isEqualTo(updatedDob);

        } finally {
            // Cleanup
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            }
        }
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
