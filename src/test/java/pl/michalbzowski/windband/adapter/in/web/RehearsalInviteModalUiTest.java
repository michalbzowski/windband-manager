package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the rehearsal detail invite flow mirrors the event detail flow:
 * <ul>
 *     <li>the detail page exposes both invite buttons,</li>
 *     <li>the invite-member modal opens,</li>
 *     <li>selecting a member and confirming adds the attendance row.</li>
 * </ul>
 */
class RehearsalInviteModalUiTest extends UiTestBase {

    @Test
    void shouldOpenInviteModalAndInviteMemberToRehearsal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "RehInv" + uid;
        String lastName = "Member" + uid;
        String fullName = firstName + " " + lastName;

        createMember(firstName, lastName, wait);

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));

        LocalDate date = LocalDate.now().plusDays(5);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = arguments[0];" +
                "document.querySelector(\"input[name='startTime']\").value = '18:00';" +
                "document.querySelector(\"input[name='endTime']\").value = '20:00';" +
                "document.querySelector(\"input[name='location']\").value = 'Sala prób';",
                date.toString());
        jsClick(driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")));

        wait.until(ExpectedConditions.urlMatches(".*/rehearsals/\\d+.*"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-group-modal-btn")));

        jsClick(driver.findElement(By.id("open-invite-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-members-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-members-modal').open === true;"));

        WebElement checkbox = driver.findElement(By.xpath(
                "//*[@id='invite-members-modal']//label[contains(text(), '" + fullName + "')]/preceding-sibling::input[@type='checkbox']"));
        checkbox.click();

        jsClick(driver.findElement(By.id("invite-selected-btn")));

        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='rehearsals-content']//select[@data-member-id and .//option[@value='NO_RESPONSE']]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='rehearsals-content']//tr[.//td[contains(text(), '" + fullName + "')]]")));

        assertThat(driver.findElements(By.xpath(
                "//*[@id='rehearsals-content']//tr[.//td[contains(text(), '" + fullName + "')]]")))
                .isNotEmpty();
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        jsClick(driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}
