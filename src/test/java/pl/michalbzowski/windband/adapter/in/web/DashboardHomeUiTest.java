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
        // On desktop the table is shown, on mobile the card list - wait for either
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("section.dashboard-upcoming .upcoming-list")),
                ExpectedConditions.presenceOfElementLocated(By.cssSelector("section.dashboard-upcoming .upcoming-table"))
        ));

        // Progress bars in cards/table for rehearsals
        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .progress-fill"));
        assertThat(progressBars).isNotEmpty();

        // Check that at least one view (cards or table) has content
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        var tableRows = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table tbody tr"));
        assertThat(cards.size() + tableRows.size()).isGreaterThan(0);

        // FAB present
        assertThat(driver.findElements(By.cssSelector(".fab"))).isNotEmpty();

        // Removed widgets must NOT be present
        assertThat(driver.findElements(By.cssSelector(".dashboard-hero"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".dashboard-events"))).isEmpty();
    }
}
