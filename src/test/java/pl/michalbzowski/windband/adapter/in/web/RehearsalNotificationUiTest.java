package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the email notification feature:
 * - REST endpoint returns correct stats
 * - Notification detail view loads and displays data
 * - Email button appears on rehearsal list rows
 */
class RehearsalNotificationUiTest extends UiTestBase {

    @Test
    void shouldReturnEmailStatsEndpointAfterCreatingRehearsal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Create a rehearsal
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));

        String today = java.time.LocalDate.now().toString();
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("18:00");

        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for redirect to list
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Wait for async notification to complete
        Thread.sleep(3000);

        // Verify the endpoint responds with valid stats via JS fetch
        var jsExecutor = (org.openqa.selenium.JavascriptExecutor) driver;
        String fetchResult = (String) jsExecutor.executeAsyncScript(
            "var callback = arguments[arguments.length - 1];" +
            "fetch('/api/rehearsals/1/email-stats')" +
            "  .then(function(res) { return res.text(); })" +
            "  .then(function(text) { callback(text); })" +
            "  .catch(function(err) { callback('ERROR: ' + err.message); });"
        );
        System.out.println("[TEST] fetch result: " + fetchResult);

        assertThat(fetchResult).startsWith("{");
        assertThat(fetchResult).contains("\"totalMembers\":2");
        assertThat(fetchResult).contains("\"successCount\":2");
        assertThat(fetchResult).contains("\"failedCount\":0");
    }

    @Test
    void shouldShowNotificationsDetailView() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Create a rehearsal
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));

        String today = java.time.LocalDate.now().toString();
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("19:00");

        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));

        // Navigate to notifications detail view directly
        driver.get(baseUrl() + "/rehearsals/1/notifications");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("notifications-content")));

        // Wait for stats to load
        Thread.sleep(3000);

        // Verify the stats elements are present
        var statTotal = driver.findElement(By.id("stat-total"));
        var statSuccess = driver.findElement(By.id("stat-success"));
        var statFailed = driver.findElement(By.id("stat-failed"));

        assertThat(statTotal).isNotNull();
        assertThat(statSuccess).isNotNull();
        assertThat(statFailed).isNotNull();

        String totalText = statTotal.getText().trim();
        String successText = statSuccess.getText().trim();
        String failedText = statFailed.getText().trim();

        System.out.println("[TEST] stat-total=" + totalText + " stat-success=" + successText + " stat-failed=" + failedText);

        // Stats should show numbers
        assertThat(totalText).matches("\\d+");
        assertThat(successText).matches("\\d+");
        assertThat(failedText).matches("\\d+");

        // Verify the member list container exists
        var memberList = driver.findElement(By.id("member-email-list"));
        assertThat(memberList).isNotNull();
    }

    @Test
    void shouldShowEmailButtonOnRehearsalListRow() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Create a rehearsal so there's at least one row
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]"));
        addButton.click();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));

        String today = java.time.LocalDate.now().toString();
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");

        var startTimeInput = driver.findElement(By.cssSelector("input[name='startTime']"));
        startTimeInput.clear();
        startTimeInput.sendKeys("20:00");

        var submitBtn = driver.findElement(
                By.cssSelector("#rehearsal-form button[type='submit'].primary"));
        submitBtn.click();

        // Wait for the full page navigation to complete (form uses setTimeout + window.location.href)
        Thread.sleep(3000);
        driver.get(baseUrl() + "/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Wait for the table to be present (not just the content div)
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//table//tr[td[@data-label='Akcje']]")));

        // After redirect to list, verify there's a 📧 button in the table rows
        var emailButtons = driver.findElements(By.xpath("//button[@title='Powiadomienia e-mail']"));
        assertThat(emailButtons).isNotEmpty();
    }
}
