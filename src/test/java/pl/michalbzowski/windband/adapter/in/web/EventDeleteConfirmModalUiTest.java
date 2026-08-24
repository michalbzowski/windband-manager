package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.event.EventCommandService;
import pl.michalbzowski.windband.application.command.event.CreateEventCommand;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventDeleteConfirmModalUiTest extends UiTestBase {

    @Autowired
    private EventCommandService eventCommandService;

    @Test
    void deleteEvent_opensConfirmModalInsteadOfNativeAlert() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAsAdmin(driver, wait);
        Long eventId = eventCommandService.createEvent(new CreateEventCommand() {{
            setName("DoUsuniecia " + System.nanoTime());
            setDate(LocalDate.now().plusDays(5));
            setStartTime(LocalTime.of(18, 0));
            setLocation("Test");
            setEventType("CONCERT");
            setPaymentType("FREE");
            setPaymentAmount(java.math.BigDecimal.ZERO);
        }}, 1L).getId();

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        String domEventId = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return document.querySelector('#events-content') ? document.querySelector('#events-content').dataset.eventId : 'MISSING';")
                .toString();
        System.out.println("DEBUG events-content data-event-id = " + domEventId);

        // Debug: check if confirm button exists
        Object confirmExists = ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return !!document.getElementById('delete-event-confirm-btn');");
        System.out.println("DEBUG delete-event-confirm-btn exists = " + confirmExists);

        // Click delete — should open a <dialog>, NOT a native confirm().
        // Delete is tucked under the ⋮ overflow on the unified bar, so open it first.
        clickOverflowInnerButton("delete-event-btn");

        WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("delete-event-modal")));
        assertThat(modal.getDomAttribute("class")).contains("app-modal");
        assertThat(modal.getTagName()).isEqualTo("dialog");

        // Cancel must close the modal and keep us on the detail page
        driver.findElement(By.id("delete-event-cancel-btn")).click();
        wait.until(ExpectedConditions.not(ExpectedConditions.attributeToBe(By.id("delete-event-modal"), "open", "open")));
        assertThat(driver.getCurrentUrl()).contains("/events/" + eventId);
    }

    @Test
    void deleteEvent_confirmDeletesAndReturnsToList() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAsAdmin(driver, wait);
        Long eventId = eventCommandService.createEvent(new CreateEventCommand() {{
            setName("DoUsuniecia2 " + System.nanoTime());
            setDate(LocalDate.now().plusDays(5));
            setStartTime(LocalTime.of(18, 0));
            setLocation("Test");
            setEventType("CONCERT");
            setPaymentType("FREE");
            setPaymentAmount(java.math.BigDecimal.ZERO);
        }}, 1L).getId();

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));

        // Delete lives under ⋮; open that first.
        clickOverflowInnerButton("delete-event-btn");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("delete-event-modal")));
        driver.findElement(By.id("delete-event-confirm-btn")).click();

        // Debug: capture browser console logs
        try {
            var logs = driver.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER);
            for (var logEntry : logs) {
                System.out.println("BROWSER LOG: " + logEntry);
            }
        } catch (Exception e) {
            System.out.println("Could not read browser logs: " + e.getMessage());
        }

        // After deletion we should be redirected away from the detail page back to the list
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/events/" + eventId)));
        assertThat(driver.getCurrentUrl()).contains("/events");
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
}
