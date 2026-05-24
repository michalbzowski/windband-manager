package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryUiTest extends UiTestBase {

    @Test
    void shouldNavigateToInventoryAndDisplayContent() {
        loginAndNavigateTo("/inventory");

        assertThat(driver.getTitle()).contains("Inwentaryzacja");

        // Page should show the heading
        var heading = driver.findElement(By.cssSelector("#inventory-content h2"));
        assertThat(heading.getText()).contains("Inwentaryzacja");

        // Page should show both sections (uniforms and instruments)
        var uniformHeading = driver.findElement(By.xpath("//h3[contains(text(), 'Stroje')]"));
        assertThat(uniformHeading).isNotNull();

        var instrumentHeading = driver.findElement(By.xpath("//h3[contains(text(), 'Instrumenty')]"));
        assertThat(instrumentHeading).isNotNull();
    }

    private void loginAndNavigateTo(String path) {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        driver.get(baseUrl() + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));
    }
}
