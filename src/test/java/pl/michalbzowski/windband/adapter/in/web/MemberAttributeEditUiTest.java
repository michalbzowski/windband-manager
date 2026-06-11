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
 * UI test reproducing issue #91: clicking "Edytuj" on a MEMBER attribute
 * throws "Unknown type: MEMBER" error.
 *
 * <p>Scenario: create a MEMBER attribute, then click "Edytuj" on it and verify
 * the edit form opens without errors.</p>
 */
class MemberAttributeEditUiTest extends UiTestBase {

    @Test
    void shouldOpenEditFormForMemberAttribute_withoutError() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String attrName = "MemAttr" + unique;

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            // === STEP 1: Navigate to MEMBER attributes list ===
            loginAndNavigateTo("/band/inventory-attributes?type=MEMBER");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content h2")));

            // === STEP 2: Create a new MEMBER attribute via UI ===
            driver.get(baseUrl() + "/band/inventory-attributes/new?type=MEMBER");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

            driver.findElement(By.cssSelector("input[name='name']")).sendKeys(attrName);
            driver.findElement(By.cssSelector("select[name='attributeType'] option[value='TEXT']")).click();

            WebElement submitBtn = driver.findElement(By.cssSelector("form button[type='submit'].primary"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", submitBtn);

            // Wait for redirect back to list
            wait.until(ExpectedConditions.urlContains("/band/inventory-attributes?type=MEMBER"));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

            // Verify the attribute appears on the list
            String listContent = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector('#content').textContent;");
            assertThat(listContent)
                    .as("Newly created MEMBER attribute should appear on the list")
                    .contains(attrName);

            // === STEP 3: Click "Edytuj" on the newly created attribute ===
            String xpath = String.format(
                    "//tr[td[contains(., '%s')]]//button[contains(text(), 'Edytuj')]", attrName);
            WebElement editBtn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn);

            // === STEP 4: Verify the edit form opens WITHOUT "Unknown type: MEMBER" error ===
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));

            // The form should have the attribute name pre-populated
            String nameValue = driver.findElement(By.cssSelector("input[name='name']")).getAttribute("value");
            assertThat(nameValue)
                    .as("Edit form should be pre-populated with the attribute name")
                    .isEqualTo(attrName);

            // Verify no error message is shown
            String pageContent = (String) ((JavascriptExecutor) driver)
                    .executeScript("return document.querySelector('#content').textContent;");
            assertThat(pageContent)
                    .as("Edit form should not contain 'Unknown type' error")
                    .doesNotContain("Unknown type");

        } finally {
            // Cleanup: delete the attribute we created
            // Navigate to the list and find the delete button
            driver.get(baseUrl() + "/band/inventory-attributes?type=MEMBER");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content")));

            try {
                String xpath = String.format(
                        "//tr[td[contains(., '%s')]]//button[contains(text(), 'Usuń')]", attrName);
                WebElement deleteBtn = driver.findElement(By.xpath(xpath));
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", deleteBtn);
                Thread.sleep(500);
            } catch (Exception ignored) {
                // Attribute may already be gone or not found — best-effort cleanup
            }
        }
    }
}
