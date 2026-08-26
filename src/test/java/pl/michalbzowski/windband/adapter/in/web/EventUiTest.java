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

        var addButton = driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#events-list-container form")));

        var formHeading = driver.findElement(By.cssSelector("#events-list-container h2"));
        assertThat(formHeading.getText()).contains("Dodaj wydarzenie");
    }
}
