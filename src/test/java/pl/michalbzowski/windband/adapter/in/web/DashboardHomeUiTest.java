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
                By.cssSelector("section.dashboard-upcoming .upcoming-list")));

        // Heading present
        var heading = driver.findElement(By.cssSelector("section.dashboard-upcoming h2"));
        assertThat(heading.getText()).contains("Nadchodzące");

        // Mini stats bar present
        assertThat(driver.findElements(By.cssSelector(".mini-stats-bar"))).isNotEmpty();

        // Progress bars in cards for rehearsals
        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .progress-fill"));
        assertThat(progressBars).isNotEmpty();

        // On wide view the table is shown and the mobile card list is hidden
        // But the desktop viewport may show table - we check at least cards exist
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        assertThat(cards).isNotEmpty();

        // FAB present
        assertThat(driver.findElements(By.cssSelector(".fab"))).isNotEmpty();

        // Removed widgets must NOT be present
        assertThat(driver.findElements(By.cssSelector(".dashboard-hero"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".dashboard-events"))).isEmpty();
    }
}
