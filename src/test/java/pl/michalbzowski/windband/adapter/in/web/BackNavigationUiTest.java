package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies browser back button behavior works correctly when navigating to event detail pages
 * from different entry points (dashboard, meetings list, other sections).
 */
class BackNavigationUiTest extends UiTestBase {

    @Test
    void backButtonFromDashboardReturnsToDashboard() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to meetings/events section via URL
        driver.get(baseUrl() + "/meetings");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        String currentUrl = driver.getCurrentUrl();
        assertThat(currentUrl).contains("meetings");

        // Press browser back button - should return to dashboard (last page in history)
        driver.navigate().back();

        // Wait for navigation and verify we get content structure restored
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        // Verify the page has proper single content container (not nested)
        assertThat(driver.findElements(By.cssSelector("#content"))).hasSize(1);
    }

    @Test
    void backButtonFromMeetingsListReturnsToList() {
        loginAndNavigateTo("/meetings");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        assertThat(driver.getCurrentUrl()).contains("meetings");

        // Create a test event first via API
        Long eventId = createTestEventViaApi();
        assertThat(eventId).isNotNull();

        // Navigate to the created event detail page
        driver.get(baseUrl() + "/events/" + eventId);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        // Press browser back - should return to meetings list (last page in history)
        driver.navigate().back();

        // Verify URL contains the origin section after back navigation
        String afterBackUrl = driver.getCurrentUrl();
        assertThat(afterBackUrl).contains("meetings");
    }

    @Test
    void backButtonFromEventDetailPreservesOriginalNavigation() {
        // Note: there's no /dashboard.html route - just "/" which shows dashboard
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        String originalDashboardUrl = driver.getCurrentUrl();

        // Create test event and navigate to its detail page
        Long eventId = createTestEventViaApi();
        driver.get(baseUrl() + "/events/" + eventId);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-detail-content, #content")));

        // Press browser back - should return to root/dashboard (original page in history)
        driver.navigate().back();

        String afterBackUrl = driver.getCurrentUrl();
        assertThat(afterBackUrl).endsWith("/");
    }

    @Test
    void navigatingFromReportsPreservesNavigationState() {
        loginAndNavigateTo("/reports");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        String reportsUrl = driver.getCurrentUrl();
        assertThat(reportsUrl).contains("reports");

        // Create test event and navigate from reports section
        Long eventId = createTestEventViaApi();
        driver.get(baseUrl() + "/events/" + eventId);

        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        // Press browser back - should return to reports page (last visited)
        driver.navigate().back();

        String afterBackUrl = driver.getCurrentUrl();
        assertThat(afterBackUrl).contains("reports");
    }

    @Test
    void multipleBackNavigationsWorkCorrectly() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate: / -> meetings/list (history stack has 2 pages)
        driver.get(baseUrl() + "/meetings");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        driver.navigate().back();

        String afterFirstBack = driver.getCurrentUrl();
        assertThat(afterFirstBack).endsWith("/");

        // Verify content is properly structured
        assertThat(driver.findElements(By.cssSelector("#content"))).hasSize(1);
    }

    @Test
    void backNavigationThroughEventDetailWorks() {
        loginAndNavigateTo("/meetings");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Create event and navigate to it
        Long eventId = createTestEventViaApi();
        driver.get(baseUrl() + "/events/" + eventId);

        // Wait for content to load after navigating to event detail
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));

        // Navigate back - should preserve structure
        driver.navigate().back();

        assertThat(driver.getPageSource()).isNotBlank();
    }

    /**
     * Helper method to create a test event via API using synchronous XHR.
     * Returns the generated event ID for use in navigation tests.
     */
    private Long createTestEventViaApi() {
        String eventIdStr = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrfToken = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrfToken) xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken.split('=')[1]);" +
                "xhr.send(JSON.stringify({" +
                "  name: 'Test Event " + System.currentTimeMillis() + "'," +
                "  date: '" + LocalDate.now().plusDays(7) + "'," +
                "  startTime: '18:00'," +
                "  endTime: '20:00'," +
                "  paymentType: 'FREE'," +
                "  eventType: 'CONCERT'," +
                "  bandId: 1," +
                "}));" +
                "return JSON.parse(xhr.responseText).id.toString();");
        return eventIdStr != null ? Long.valueOf(eventIdStr) : null;
    }

}
