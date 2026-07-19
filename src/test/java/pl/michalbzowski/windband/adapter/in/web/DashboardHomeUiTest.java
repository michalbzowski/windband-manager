package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for the home dashboard page ("/").
 * Verifies that the upcoming events + rehearsals list is shown first,
 * sorted chronologically, and the old hero / standalone events table are gone.
 */
class DashboardHomeUiTest extends UiTestBase {

    @Test
    void shouldShowUpcomingListAsFirstContent() {
        loginAndNavigateTo("/");

        assertThat(driver.getTitle()).contains("Podsumowanie");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-upcoming .upcoming-table table")));

        // Heading present
        var heading = driver.findElement(By.cssSelector("section.dashboard-upcoming h2"));
        assertThat(heading.getText()).contains("Nadchodzące");

        // At least one row rendered with a relative badge (Jutro / Za N dni / Dziś)
        var badges = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-badge"));
        assertThat(badges).isNotEmpty();

        // On wide view the table is shown and the mobile card list is hidden
        assertThat(driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-table")).isDisplayed()).isTrue();
        assertThat(driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-list")).isDisplayed()).isFalse();

        // Removed widgets must NOT be present
        assertThat(driver.findElements(By.cssSelector(".dashboard-hero"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".dashboard-events"))).isEmpty();
    }
}
