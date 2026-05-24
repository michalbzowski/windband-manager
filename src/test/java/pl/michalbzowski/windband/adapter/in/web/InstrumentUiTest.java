package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentUiTest extends UiTestBase {

    @Test
    void shouldNavigateToInstrumentsAndOpenNewForm() {
        loginAndNavigateTo("/instruments");

        assertThat(driver.getTitle()).contains("Instrumenty");

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj instrument')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#instrument-form")));

        var formHeading = driver.findElement(By.cssSelector("#instruments-content h2"));
        assertThat(formHeading.getText()).contains("Dodaj instrument");
    }
}
