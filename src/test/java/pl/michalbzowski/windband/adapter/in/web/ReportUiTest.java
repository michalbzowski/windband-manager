package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReportUiTest extends UiTestBase {

    @Test
    void shouldNavigateToReportsAndGenerateMonthlyReport() {
        loginAndNavigateTo("/reports");

        assertThat(driver.getTitle()).contains("Raporty");

        var generateButton = driver.findElement(By.cssSelector("button[type='submit'].primary"));
        assertThat(generateButton.getText()).contains("Generuj raport");

        generateButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#reports-content h2"), "Raport miesięczny"));

        var reportHeading = driver.findElement(By.cssSelector("#reports-content h2"));
        assertThat(reportHeading.getText()).contains("Raport miesięczny");

        var memberStats = driver.findElement(By.cssSelector("#reports-content h4"));
        assertThat(memberStats.getText()).contains("Statystyki członków");

        var backButton = driver.findElement(By.cssSelector("#reports-content button.secondary"));
        assertThat(backButton.getText()).contains("Powrót");
    }
}
