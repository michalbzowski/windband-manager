package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

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
}
