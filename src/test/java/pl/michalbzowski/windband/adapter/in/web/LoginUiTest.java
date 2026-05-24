package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginUiTest extends UiTestBase {

    @Test
    void shouldLoginSuccessfully() {
        driver.get(baseUrl() + "/login");

        assertThat(driver.getTitle()).contains("Logowanie");

        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        // Wait for JS fetch login to complete and redirect to /
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        // After login, should redirect to dashboard (not /login anymore)
        assertThat(driver.getCurrentUrl()).doesNotContain("/login");
    }
}
