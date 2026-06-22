package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for admin dashboard management.
 * Tests: admin navigates to dashboard management, sees sync button and dashboard list.
 */
class DashboardAdminUiTest extends UiTestBase {

    @Test
    void shouldNavigateToAdminDashboardsPage() {
        loginAndNavigateTo("/admin/dashboards");

        // Should see the admin dashboards page
        assertThat(driver.getTitle()).contains("Zarządzanie dashboardami");

        // Should see sync button
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("admin-dashboards-content")));

        var syncButton = driver.findElements(By.cssSelector("#admin-dashboards-content button"));
        assertThat(syncButton).isNotEmpty();
    }

    @Test
    void shouldShowSyncButton() {
        loginAndNavigateTo("/admin/dashboards");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("admin-dashboards-content")));

        var content = driver.findElement(By.id("admin-dashboards-content")).getText();
        assertThat(content).contains("Synchronizuj");
    }
}
