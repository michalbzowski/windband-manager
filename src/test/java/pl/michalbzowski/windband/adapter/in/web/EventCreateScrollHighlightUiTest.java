package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EventCreateScrollHighlightUiTest extends UiTestBase {

    @Test
    void eventListRowsHaveIdsForScrollAndHighlight() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAsAdmin(driver, wait);

        // Go to the "add event" form and submit it
        driver.get(baseUrl() + "/events/new");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("event-form")));
        String name = "NoweWydarzenie " + System.nanoTime();
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='name']\").value = arguments[0];" +
                "document.querySelector(\"input[name='date']\").value = arguments[1];" +
                "document.querySelector(\"input[name='startTime']\").value = arguments[2];" +
                "document.querySelector(\"input[name='location']\").value = arguments[3];",
                name, java.time.LocalDate.now().plusDays(5).toString(), "18:00", "Test");
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();

        // After submit we should be back on the events LIST (full reload to /events?focus=...),
        // not still on the /events/new form page.
        wait.until(ExpectedConditions.and(
                ExpectedConditions.urlContains("/events"),
                ExpectedConditions.not(ExpectedConditions.urlContains("/new"))));
        // Wait for the just-created event row specifically (by its unique name) so we never
        // match a leftover/stale row that the /events/new form page might already render.
        WebElement row = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[@id='events-list-container']//tr[contains(@id,'event-') and contains(., '"
                        + name + "')]")));
        assertThat(row.getAttribute("id")).startsWith("event-");
        assertThat(row.getText()).contains(name);
    }

    private WebDriver getDriver() {
        return driver;
    }

    private void loginAsAdmin(WebDriver driver, WebDriverWait wait) {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
    }

    private void fillField(String name, String value) {
        WebElement el = driver.findElement(By.name(name));
        el.clear();
        el.sendKeys(value);
    }
}
