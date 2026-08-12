package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.support.ui.ExpectedConditions.*;

public class MeetingListUiTest extends UiTestBase {

    @Test
    void meetingListLoads_showsSeededEvents() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to /meetings
        loginAndNavigateTo("/meetings");
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // Test DB has seeded events (from data.sql: future concert + past parade)
        // So empty state should NOT show; instead we should see the event rows
        WebElement table = wait.until(presenceOfElementLocated(By.tagName("table")));
        assertThat(table.isDisplayed()).isTrue();

        // Verify at least one upcoming meeting row exists (the future concert from data.sql)
        WebElement upcomingRow = wait.until(presenceOfElementLocated(
                By.xpath("//tr[contains(@id, 'meeting-')]")));
        assertThat(upcomingRow.isDisplayed()).isTrue();

        // And the badge shows "Koncert bezpłatny"
        assertThat(driver.getPageSource()).contains("Koncert bezpłatny");
    }

    @Test
    void newMeetingButton_navigatesToNewMeetingForm() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Given
        loginAndNavigateTo("/meetings");
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // When clicking "Dodaj spotkanie"
        WebElement newBtn = wait.until(elementToBeClickable(
                By.xpath("//button[contains(text(), 'Dodaj spotkanie')]")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", newBtn);

        // Then new meeting form loads
        wait.until(presenceOfElementLocated(By.id("meetings-content")));
        WebElement title = wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(text(), 'Nowe spotkanie')]")));
        assertThat(title.isDisplayed()).isTrue();

        // And the four options are present
        assertThat(driver.getPageSource()).contains("Próba regularna");
        assertThat(driver.getPageSource()).contains("Próba ad-hoc");
        assertThat(driver.getPageSource()).contains("Koncert bezpłatny");
        assertThat(driver.getPageSource()).contains("Koncert płatny");
    }

    @Test
    void createAdHocRehearsal_redirectsToRehearsalDetail() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Given
        loginAndNavigateTo("/meetings");
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // Click "Dodaj spotkanie" to load the form
        WebElement newBtn = wait.until(elementToBeClickable(
                By.xpath("//button[contains(text(), 'Dodaj spotkanie')]")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", newBtn);

        // Wait for form to load
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // When clicking "Próba ad-hoc — TERAZ"
        WebElement adhocBtn = wait.until(elementToBeClickable(
                By.xpath("//button[contains(text(), 'TERAZ i id')]")));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", adhocBtn);

        // Then redirected to rehearsal detail (wait for detail page)
        wait.until(urlContains("/rehearsals/"));

        // Debug: print current URL
        String currentUrl = driver.getCurrentUrl();
        System.out.println("[TEST] current URL after ad-hoc create: " + currentUrl);

        wait.until(presenceOfElementLocated(By.id("rehearsals-content")));

        // And we see the attendance section
        WebElement attendanceHeader = wait.until(presenceOfElementLocated(
                By.xpath("//h3[contains(text(), 'Lista obecności')]")));
        assertThat(attendanceHeader.isDisplayed()).isTrue();
    }

    @Test
    void szczegolyButton_upcomingEvent_navigatesToEventDetail() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Given: navigate to /meetings page
        loginAndNavigateTo("/meetings");
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // Find the Szczegóły link for an upcoming meeting/event (first one on page)
        WebElement szczegolyLink = wait.until(elementToBeClickable(
                By.xpath("//a[contains(text(), '📋 Szczegóły')]")));

        assertThat(szczegolyLink.isDisplayed()).isTrue();

        // When: click the Szczegóły link (full-page navigation now)
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", szczegolyLink);

        // Then: wait for URL to change to /events/{id} or /rehearsals/{id}
        wait.until(not(urlContains("/meetings")));

        // And: verify we're on the detail page with proper headings
        assertThat(driver.getCurrentUrl()).containsAnyOf("/events/", "/rehearsals/");

        // Check for event or rehearsal specific content
        WebElement title = wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(text(), 'Szczegóły')]")));
        assertThat(title.isDisplayed()).isTrue();
    }

    @Test
    void szczegolyButton_pastEvent_navigatesToEventDetail() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        // Given: navigate to /meetings page and scroll to past events section
        loginAndNavigateTo("/meetings");
        wait.until(presenceOfElementLocated(By.id("meetings-content")));

        // Check if there's a "Przeszłe spotkania" section with Szczegóły links
        WebElement pastSection = driver.findElement(By.xpath("//h3[contains(text(), 'Przeszłe')]"));

        // Scroll into view on mobile might be needed - use JS to scroll
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", pastSection);

        // Find Szczegóły links in the past events section (look for table row with class 'past-item')
        WebElement pastDetailsLink = wait.until(elementToBeClickable(
                By.xpath("//tr[@class='past-item']//a[contains(text(), '📋 Szczegóły')]")));

        assertThat(pastDetailsLink.isDisplayed()).isTrue();

        // When: click the Szczegóły link for a past event/rehearsal
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", pastDetailsLink);

        // Then: verify full page navigation works (not HTMX fragment swap) - URL should no longer be /meetings
        wait.until(not(urlContains("/meetings")));

        String finalUrl = driver.getCurrentUrl();
        assertThat(finalUrl).containsAnyOf("/events/", "/rehearsals/");

        // Verify detail page loads with proper content
        WebElement title = wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(@class, 'app-modal-header-text') or contains(text(), 'Szczegóły')]")));
        assertThat(title.isDisplayed()).isTrue();
    }
}
