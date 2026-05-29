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

        // The controller returns inventory-attributes template with h2 "Atrybuty"
        var heading = driver.findElement(By.cssSelector("#content h2"));
        assertThat(heading.getText()).contains("Atrybuty");

        // Verify the "Dodaj atrybut" button exists
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj atrybut')]"));
        assertThat(addButton).isNotNull();
    }

    @Test
    void shouldOpenNewAttributeForm() {
        loginAndNavigateTo("/band/attributes");

        assertThat(driver.getTitle()).contains("Atrybuty");

        // Click "Dodaj atrybut" button
        var addBtn = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj atrybut')]"));
        addBtn.click();

        // The form page loads (full page navigation via HTMX swap innerHTML on body)
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#content form")));

        // Verify form has name input
        var nameInput = driver.findElement(By.cssSelector("#content form input[name='name']"));
        assertThat(nameInput).isNotNull();
    }
}
