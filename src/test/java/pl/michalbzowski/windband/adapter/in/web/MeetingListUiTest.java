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
}