package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
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

        // Find the monthly report form and submit it
        var generateButton = driver.findElement(By.cssSelector("form[hx-get='/reports/generate'] button[type='submit'].btn-primary"));
        assertThat(generateButton.getText()).contains("Generuj");

        generateButton.click();

        var wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("#reports-content h2"), "Raport miesięczny"));

        var reportHeading = driver.findElement(By.cssSelector("#reports-content h2"));
        assertThat(reportHeading.getText()).contains("Raport miesięczny");

        var memberStats = driver.findElement(By.cssSelector("#reports-content h4"));
        assertThat(memberStats.getText()).contains("Statystyki członków");

        var backButton = driver.findElement(By.cssSelector("#reports-content button.secondary"));
        assertThat(backButton.getText()).contains("Powrót");
    }

    @Test
    void shouldShowReportCardsOnReportsPage() {
        loginAndNavigateTo("/reports");

        assertThat(driver.getTitle()).contains("Raporty");

        // Check that report cards are present
        var reportCards = driver.findElements(By.cssSelector(".report-card"));
        assertThat(reportCards).hasSizeGreaterThan(0);

        // Check for the monthly report card (visible to all authenticated users)
        var cards = driver.findElements(By.cssSelector(".report-card h3"));
        var cardTitles = cards.stream().map(WebElement::getText).toList();

        assertThat(cardTitles).anyMatch(t -> t.contains("Raport miesięczny"));

        // Admin-only cards are hidden for non-admin users (ROLE_USER in test)
        // In a real admin test we'd check for "Sprawozdanie" and "Hello World" too
    }
}
