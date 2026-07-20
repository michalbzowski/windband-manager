package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that clicking "Zapisz obecność" (Save attendance) shows a success toast.
 *
 * <p>Regression guard for the bug where {@code saveRehearsalAttendance()} used a raw
 * {@code fetch()} instead of {@code fetchWithToast}, so no toast appeared after saving.</p>
 */
class RehearsalAttendanceToastUiTest extends UiTestBase {

    @Test
    void clickingSaveAttendance_showsSuccessToast() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Toast" + uid;
        String lastName = "Test" + uid;

        // --- Create a member via UI ---
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
        Thread.sleep(1000);

        // --- Create a rehearsal via UI ---
        loginAndNavigateTo("/rehearsals");
        driver.findElement(By.xpath("//button[contains(text(), 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");
        driver.findElement(By.cssSelector("input[name='startTime']")).sendKeys("18:00");
        driver.findElement(By.cssSelector("input[name='endTime']")).sendKeys("20:00");
        driver.findElement(By.cssSelector("input[name='location']")).sendKeys("Sala prób");
        driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));
        Thread.sleep(1500);

        // --- Open rehearsal detail ---
        driver.get(baseUrl() + "/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        List<WebElement> detailBtns = driver.findElements(By.xpath("//button[contains(text(), 'Szczegóły')]"));
        assertThat(detailBtns).isNotEmpty();
        detailBtns.get(0).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));
        // Reload the detail page as a full page load so the inline <script> that
        // defines window.saveRehearsalAttendance() is executed (HTMX-injected
        // fragments do not run scripts).
        String rehearsalId = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('rehearsals-content').getAttribute('data-rehearsal-id');");
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));

        // --- Change first member's status to PRESENT (so a save request is sent) ---
        WebElement select = driver.findElement(By.cssSelector("#rehearsals-content .status-select"));
        ((JavascriptExecutor) driver).executeScript(
                "var s = arguments[0]; s.value = 'PRESENT'; s.dispatchEvent(new Event('change', {bubbles:true}));",
                select);

        // --- Click "Zapisz obecność" ---
        WebElement saveBtn = driver.findElement(By.id("save-attendance-btn"));
        saveBtn.click();

        // --- Assert a success toast appears ---
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("toast-container")));
        WebDriverWait toastWait = new WebDriverWait(driver, Duration.ofSeconds(8));
        toastWait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        String toastText = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('toast-container').textContent;");
        System.out.println("[TEST] toast-container text: '" + toastText + "'");
        assertThat(toastText)
                .as("Clicking 'Zapisz obecność' must show a success toast")
                .contains("Zapisano obecność");
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
