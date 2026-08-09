package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class ReportUiTest extends UiTestBase {

    @Test
    void shouldNavigateToReportsAndGenerateMonthlyReport() {
        loginAndNavigateTo("/reports");

        assertThat(driver.getTitle()).contains("Raporty");

        // The reports page should contain the monthly report card and its generate button
        // Now using dynamic Jasper reports - look for the monthly report card
        var generateButton = driver.findElement(By.cssSelector("#reports-content form[hx-get='/reports/generate'] button[type='submit'].btn-primary"));
        assertThat(generateButton.getText()).contains("Generuj");

        String pageHtml = driver.getPageSource();
        assertThat(pageHtml).contains("Raport miesięczny");
        assertThat(pageHtml).contains("/reports/generate");
    }

    @Test
    void shouldShowReportCardsOnReportsPage() {
        loginAndNavigateTo("/reports");

        assertThat(driver.getTitle()).contains("Raporty");

        // Check that report cards are present - now includes dynamic Jasper reports
        var reportCards = driver.findElements(By.cssSelector(".report-card"));
        assertThat(reportCards).hasSizeGreaterThan(0);

        // Check for the monthly report card (visible to all authenticated users)
        var cards = driver.findElements(By.cssSelector(".report-card h3"));
        var cardTitles = cards.stream().map(WebElement::getText).toList();

        assertThat(cardTitles).anyMatch(t -> t.contains("Raport miesięczny"));

        // Should also have Jasper reports now (sprawozdanie-miesieczne, hello)
        assertThat(cardTitles).anyMatch(t -> t.contains("Sprawozdanie") || t.contains("HelloWorld"));
    }
}
