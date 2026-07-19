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
 * Verifies that the events table is shown first and the old hero /
 * "upcoming items" widgets are gone.
 */
class DashboardHomeUiTest extends UiTestBase {

    @Test
    void shouldShowEventsTableAsFirstContent() {
        loginAndNavigateTo("/");

        assertThat(driver.getTitle()).contains("Podsumowanie");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-events table")));

        // Events table heading present
        var heading = driver.findElement(By.cssSelector("section.dashboard-events h2"));
        assertThat(heading.getText()).contains("Wydarzenia");

        // Removed widgets must NOT be present
        assertThat(driver.findElements(By.cssSelector(".dashboard-hero"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".dashboard-upcoming"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".upcoming-card"))).isEmpty();
    }
}
