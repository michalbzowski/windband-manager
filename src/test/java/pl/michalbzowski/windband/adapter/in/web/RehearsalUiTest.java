package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RehearsalUiTest extends UiTestBase {

    @Test
    void shouldNavigateToRehearsalsAndOpenNewForm() {
        loginAndNavigateTo("/rehearsals");

        assertThat(driver.getTitle()).contains("Próby");

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj próbę')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        // HTMX loads form into #rehearsals-content
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content form[hx-post]")));

        var formHeading = driver.findElement(By.cssSelector("#rehearsals-content h2"));
        assertThat(formHeading.getText()).contains("Zaplanuj próbę");
    }
}
