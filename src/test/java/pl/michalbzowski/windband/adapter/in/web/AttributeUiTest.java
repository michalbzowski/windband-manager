package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeUiTest extends UiTestBase {

    @Test
    void shouldNavigateToAttributesPage() {
        loginAndNavigateTo("/band/attributes");

        assertThat(driver.getTitle()).contains("Atrybuty");

        var heading = driver.findElement(By.cssSelector("#attribute-defs-content h2"));
        assertThat(heading.getText()).contains("Atrybuty muzyków");

        var addButton = driver.findElement(By.id("add-attr-btn"));
        assertThat(addButton).isNotNull();
        assertThat(addButton.getText()).contains("Dodaj atrybut");

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("new-attr-form")));

        var formHeading = driver.findElement(By.cssSelector("#new-attr-form h4"));
        assertThat(formHeading.getText()).contains("Nowy atrybut");

        var nameInput = driver.findElement(By.id("attr-name"));
        assertThat(nameInput).isNotNull();

        var typeSelect = driver.findElement(By.id("attr-type"));
        assertThat(typeSelect).isNotNull();

        var cancelButton = driver.findElement(By.id("cancel-attr-btn"));
        cancelButton.click();

        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.id("new-attr-form")));
        var newForm = driver.findElement(By.id("new-attr-form"));
        assertThat(newForm.getCssValue("display")).isEqualTo("none");
    }

    @Test
    void shouldAddAttributeAndAppearInList() {
        loginAndNavigateTo("/band/attributes");

        assertThat(driver.getTitle()).contains("Atrybuty");

        // Initially the list may be empty or have existing attributes
        // Count existing rows before adding
        List<WebElement> rowsBefore = driver.findElements(By.cssSelector("#attrs-list table tbody tr"));
        int countBefore = rowsBefore.size();

        // Debug: check page state
        System.out.println("=== PAGE STATE ===");
        System.out.println("Title: " + driver.getTitle());
        System.out.println("URL: " + driver.getCurrentUrl());
        var addBtn = driver.findElement(By.id("add-attr-btn"));
        System.out.println("add-attr-btn found: " + addBtn.isDisplayed());
        var newForm = driver.findElement(By.id("new-attr-form"));
        System.out.println("new-attr-form display: " + newForm.getCssValue("display"));
        System.out.println("=== END ===");

        // Click "Dodaj atrybut"
        addBtn.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("new-attr-form")));

        // Fill in the form
        var nameInput = driver.findElement(By.id("attr-name"));
        nameInput.clear();
        nameInput.sendKeys("Test Atrybut Selenium");

        // Use JS to set select value (Selenium click on <option> can be flaky)
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
            "document.getElementById('attr-type').value = 'TEXT';");

        var orderInput = driver.findElement(By.id("attr-order"));
        orderInput.clear();
        orderInput.sendKeys("1");

        // Save
        var saveButton = driver.findElement(By.id("save-attr-btn"));
        saveButton.click();

        // Wait for the list to update
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        // Debug: check cookies
        var cookies = driver.manage().getCookies();
        System.out.println("=== COOKIES ===");
        for (var c : cookies) {
            System.out.println("Cookie: name=" + c.getName() + " domain=" + c.getDomain() + " value=" + (c.getValue() != null ? c.getValue().substring(0, Math.min(20, c.getValue().length())) + "..." : "null"));
        }
        System.out.println("=== END COOKIES ===");

        // Debug: sync XHR to check API
        String apiResult;
        try {
            apiResult = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('GET', '/api/bands/1/attribute-defs', false);" +
                "xhr.withCredentials = true;" +
                "xhr.send();" +
                "return 'XHR STATUS:' + xhr.status + ' RESP:' + xhr.responseText.substring(0, 300);");
        } catch (Exception e) {
            apiResult = "JS_ERROR: " + e.getMessage();
        }
        System.out.println("=== XHR /api/bands/1/attribute-defs ===");
        System.out.println(apiResult);
        System.out.println("=== END ===");

        // Wait for the list to update — the new attribute should appear
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#attrs-list table tbody tr")));

        // Debug: print page source
        System.out.println("=== ATTR LIST PAGE SOURCE ===");
        var attrsList = driver.findElement(By.id("attrs-list"));
        System.out.println(attrsList.getText());
        System.out.println("=== END ATTR LIST ===");

        // Verify the new attribute is in the list
        var nameCell = driver.findElement(By.xpath("//td[contains(text(), 'Test Atrybut Selenium')]"));
        assertThat(nameCell).isNotNull();
        assertThat(nameCell.getText()).isEqualTo("Test Atrybut Selenium");

        // Verify type shows as "Tekst"
        var typeCell = driver.findElement(By.xpath("//td[contains(text(), 'Tekst')]"));
        assertThat(typeCell).isNotNull();
    }
}
