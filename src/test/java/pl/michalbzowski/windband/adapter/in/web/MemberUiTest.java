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

        assertThat(driver.getTitle()).contains("Muzycy");

        var addButton = driver.findElement(By.xpath("//button[contains(text(), 'Dodaj muzyka')]"));
        assertThat(addButton).isNotNull();

        addButton.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        var formHeading = driver.findElement(By.cssSelector("#members-content h2"));
        assertThat(formHeading.getText()).contains("Dodaj muzyka");
    }
}
