package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EventUiTest extends UiTestBase {

    @Test
    void shouldNavigateToEventsAndOpenNewForm() {
        loginAndNavigateTo("/events");

        assertThat(driver.getTitle()).contains("Wydarzenia");

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj wydarzenie')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#events-content form[hx-post]")));

        var formHeading = driver.findElement(By.cssSelector("#events-content h2"));
        assertThat(formHeading.getText()).contains("Dodaj wydarzenie");
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
