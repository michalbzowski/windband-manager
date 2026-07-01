package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryUiTest extends UiTestBase {

    @Test
    void shouldNavigateToInventoryAndDisplayContent() {
        loginAndNavigateTo("/inventory");

        assertThat(driver.getTitle()).contains("Zasoby");

        var heading = driver.findElement(By.cssSelector("#inventory-content h2"));
        assertThat(heading.getText()).contains("Zasoby");

        var uniformHeading = driver.findElement(By.xpath("//h3[contains(text(), 'Ekwipunek')]"));
        assertThat(uniformHeading).isNotNull();

        var instrumentHeading = driver.findElement(By.xpath("//h3[contains(text(), 'Instrumenty')]"));
        assertThat(instrumentHeading).isNotNull();
    }
}
