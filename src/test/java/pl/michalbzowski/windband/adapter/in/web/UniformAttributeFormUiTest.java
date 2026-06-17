package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDef;
import pl.michalbzowski.windband.domain.inventory.UniformAttributeDefRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI test: verify that active SELECT attribute appears in uniform add form
 * with correct options, and inactive attribute does not appear.
 */
class UniformAttributeFormUiTest extends UiTestBase {

    private static final String[] OPTIONS = {"Mundur", "Koszula", "Czapka"};

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private UniformAttributeDefRepository uniformAttrDefRepository;

    @Test
    void activeSelectAttributeAppearsInUniformForm_withCorrectOptions() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String attrName = "Rodzaj-" + unique;
        Band band = bandRepository.findById(1L).orElseThrow();

        // Create active SELECT attribute
        UniformAttributeDef attrDef = UniformAttributeDef.create(
                band, attrName, "SELECT", true, true, 0,
                String.join(", ", OPTIONS));
        attrDef = uniformAttrDefRepository.save(attrDef);
        Long attrDefId = attrDef.getId();

        try {
            // Navigate to inventory → Uniforms tab
            loginAndNavigateTo("/inventory");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content")));

            WebElement strojeTab = driver.findElement(By.cssSelector("button[data-tab='uniforms']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", strojeTab);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#uniforms-content")));

            // Click "Dodaj stroj"
            WebElement addBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#uniforms-content button[onclick='showUniformForm()']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addBtn);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#uniform-form")));

            // Verify attribute select is present
            String attrSelectId = "uni-attr-" + attrDefId;
            WebElement attrSelectEl = wait.until(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("#" + attrSelectId)));

            // Verify label — find the div containing the select, then get its label
            WebElement selectDiv = driver.findElement(By.xpath(
                    "//div[@id='uniform-attributes']//div[.//select[@id='" + attrSelectId + "']]"));
            WebElement label = selectDiv.findElement(By.cssSelector("label"));
            assertThat(label.getText())
                    .as("Attribute label should match")
                    .isEqualTo(attrName);

            // Verify options
            Select attrSelect = new Select(attrSelectEl);
            assertThat(attrSelect.getOptions()).hasSize(OPTIONS.length + 1); // +1 for "Wybierz..."

            for (int i = 0; i < OPTIONS.length; i++) {
                String optionText = attrSelect.getOptions().get(i + 1).getText();
                assertThat(optionText)
                        .as("Option " + i + " should be: " + OPTIONS[i])
                        .isEqualTo(OPTIONS[i]);
            }

            // Verify the attribute div exists in the form
            WebElement attrDiv = driver.findElement(By.cssSelector(
                    "#uniform-attributes .attr-field"));
            assertThat(attrDiv).isNotNull();

        } finally {
            uniformAttrDefRepository.findById(attrDefId).ifPresent(uniformAttrDefRepository::delete);
        }
    }

    @Test
    void inactiveAttributeDoesNotAppearInUniformForm() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String attrName = "Inactive-" + unique;
        Band band = bandRepository.findById(1L).orElseThrow();

        // Create INACTIVE SELECT attribute
        UniformAttributeDef attrDef = UniformAttributeDef.create(
                band, attrName, "SELECT", true, true, 0,
                "Opcja1, Opcja2");
        attrDef.setActive(false);
        attrDef = uniformAttrDefRepository.save(attrDef);
        Long attrDefId = attrDef.getId();

        try {
            // Navigate to inventory → Uniforms tab
            loginAndNavigateTo("/inventory");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#inventory-content")));

            WebElement strojeTab = driver.findElement(By.cssSelector("button[data-tab='uniforms']"));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", strojeTab);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#uniforms-content")));

            // Click "Dodaj stroj"
            WebElement addBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#uniforms-content button[onclick='showUniformForm()']")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", addBtn);

            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#uniform-form")));

            // Verify attribute select is NOT present
            String attrSelectId = "uni-attr-" + attrDefId;
            boolean isPresent = !driver.findElements(By.cssSelector("#" + attrSelectId)).isEmpty();
            assertThat(isPresent)
                    .as("Inactive attribute should NOT appear in uniform form")
                    .isFalse();

        } finally {
            uniformAttrDefRepository.findById(attrDefId).ifPresent(uniformAttrDefRepository::delete);
        }
    }
}
