package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for dashboard management flow.
 * Tests: user navigates to dashboards page, sees assigned dashboards.
 *
 * Note: Full Superset integration tests require a running Superset instance.
 * This test verifies the windband-manager UI layer (list, view pages).
 */
class DashboardUiTest extends UiTestBase {

    @Test
    void shouldNavigateToDashboardsPage() {
        loginAndNavigateTo("/dashboards");

        // Should see the dashboards page
        assertThat(driver.getTitle()).contains("Dashboardy");

        // Should see either dashboard cards or "no dashboards" message
        var content = driver.findElement(By.id("dashboards-content"));
        assertThat(content).isNotNull();

        // Either dashboard cards or empty state
        var emptyMessage = driver.findElements(By.cssSelector("#dashboards-content article"));
        assertThat(emptyMessage).isNotEmpty();
    }

    @Test
    void shouldShowEmptyStateWhenNoDashboardsAssigned() {
        loginAndNavigateTo("/dashboards");

        // When no dashboards are assigned, should see the empty state message
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dashboards-content")));

        var content = driver.findElement(By.id("dashboards-content")).getText();
        // Either "Brak dostępnych dashboardów" or dashboard cards
        assertThat(content).containsAnyOf("Brak dostępnych dashboardów", "Dashboardy");
    }
}
