package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MemberUiTest extends UiTestBase {

    @Test
    void shouldNavigateToMembersAndOpenNewForm() {
        loginAndNavigateTo("/members");

        // We should be on members page
        assertThat(driver.getTitle()).contains("Muzycy");

        // Page should show "Dodaj muzyka" button
        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj muzyka')]"));
        assertThat(addButton).isNotNull();

        // Click "Dodaj muzyka" — loads form via HTMX
        addButton.click();

        // Wait for HTMX to load the form (look for the form or heading)
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        // Form should be visible with "Dodaj muzyka" heading
        var formHeading = driver.findElement(By.cssSelector("#members-content h2"));
        assertThat(formHeading.getText()).contains("Dodaj muzyka");
    }

    private void loginAndNavigateTo(String path) {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for login redirect
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        // Navigate to target page
        driver.get(baseUrl() + path);

        // Wait for page to fully load (HTMX content loaded)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));
    }
}
