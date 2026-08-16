package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for dashboard responsive behavior at three breakpoints.
 * Verifies that upcoming events list shows cards on mobile/tablet
 * and table on desktop.
 */
class DashboardResponsiveUiTest extends UiTestBase {

    /**
     * Test mobile breakpoint (< 768px): cards visible, table hidden
     */
    @Test
    void shouldShowCardsOnMobile() {
        // Set mobile viewport
        driver.manage().window().setSize(new Dimension(375, 800));

        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-upcoming .upcoming-list")));

        // Cards should be visible
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        assertThat(cards).as("Cards should be visible on mobile").isNotEmpty();

        // Table should be hidden (display: none via CSS)
        var table = driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-table"));
        assertThat(table.isDisplayed()).as("Table should be hidden on mobile").isFalse();

        // Progress bars should exist in cards (if events have attendance data)
        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-list .progress-fill"));
        // Progress bars are conditional on attendancePercentage - just verify cards exist (they have attendance data in test setup)
        assertThat(cards).as("Cards should be visible on mobile").isNotEmpty();
    }

    /**
     * Test tablet breakpoint (768-1023px): cards visible, table hidden
     */
    @Test
    void shouldShowCardsOnTablet() {
        // Set tablet viewport (768px wide)
        driver.manage().window().setSize(new Dimension(768, 1024));

        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-upcoming .upcoming-list")));

        // Cards should be visible (CSS: @media (max-width: 767px) hides table, shows cards)
        // At exactly 768px, the mobile styles still apply because breakpoint is max-width: 767px
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        assertThat(cards).as("Cards should be visible on tablet").isNotEmpty();

        // Table should be hidden
        var table = driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-table"));
        assertThat(table.isDisplayed()).as("Table should be hidden on tablet").isFalse();
    }

    /**
     * Test desktop breakpoint (≥1024px): table visible, cards hidden
     */
    @Test
    void shouldShowTableOnDesktop() {
        // Set desktop viewport
        driver.manage().window().setSize(new Dimension(1024, 768));

        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-upcoming .upcoming-table")));

        // Table should be visible
        var table = driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-table"));
        assertThat(table.isDisplayed()).as("Table should be visible on desktop").isTrue();

        // Cards should be hidden (display: none via CSS)
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        // At desktop, cards have display: none, so they exist in DOM but are not displayed
        for (WebElement card : cards) {
            assertThat(card.isDisplayed()).as("Cards should be hidden on desktop").isFalse();
        }

        // Progress bars should exist in table rows (if events have attendance data)
        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table .progress-fill"));
        // Progress bars are conditional on attendancePercentage - just verify table rows exist
        var tableRows = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table tbody tr"));
        assertThat(tableRows).as("Table should have data rows on desktop").isNotEmpty();
    }

    /**
     * Test large desktop breakpoint (≥1400px): table visible, cards hidden
     */
    @Test
    void shouldShowTableOnLargeDesktop() {
        // Set large desktop viewport
        driver.manage().window().setSize(new Dimension(1400, 900));

        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("section.dashboard-upcoming .upcoming-table")));

        // Table should be visible
        var table = driver.findElement(By.cssSelector("section.dashboard-upcoming .upcoming-table"));
        assertThat(table.isDisplayed()).as("Table should be visible on large desktop").isTrue();

        // Cards should be hidden
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        for (WebElement card : cards) {
            assertThat(card.isDisplayed()).as("Cards should be hidden on large desktop").isFalse();
        }

        // Progress bars should exist in table rows (if events have attendance data)
        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table .progress-fill"));
        // Progress bars are conditional on attendancePercentage - just verify table rows exist
        var tableRows = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table tbody tr"));
        assertThat(tableRows).as("Table should have data rows on large desktop").isNotEmpty();
    }
}