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
 * Verifies the quick-attendance modal opened from the rehearsal detail view:
 * one member at a time, status buttons, save-then-advance, back button,
 * auto-close after the last member, and persistence across reload.
 */
class QuickAttendanceModalUiTest extends UiTestBase {

    @Test
    void quickAttendanceModal_savesAndAdvancesAndPersists() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "Quick" + uid;
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

        // --- Open rehearsal detail (full page load so inline script + openAppModal run) ---
        driver.get(baseUrl() + "/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        List<WebElement> detailBtns = driver.findElements(By.xpath("//button[contains(text(), 'Szczegóły')]"));
        assertThat(detailBtns).isNotEmpty();
        detailBtns.get(0).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));
        String rehearsalId = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('rehearsals-content').getAttribute('data-rehearsal-id');");
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));

        int memberCount = driver.findElements(By.cssSelector("#rehearsals-content tbody tr")).size();
        assertThat(memberCount).isGreaterThanOrEqualTo(2);
        System.out.println("[TEST] memberCount=" + memberCount);

        // --- Open quick attendance modal ---
        driver.findElement(By.id("quick-attendance-btn")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("quick-attendance-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === true;"));
        assertThat(isModalOpen()).isTrue();

        // progress shows 1 / N
        String progress1 = driver.findElement(By.id("qa-progress")).getText();
        System.out.println("[TEST] progress after open: " + progress1);
        assertThat(progress1).startsWith("1 /");

        // --- Member 1: click PRESENT -> should advance to 2 / N ---
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
        Thread.sleep(600);
        String progress2 = driver.findElement(By.id("qa-progress")).getText();
        System.out.println("[TEST] progress after first save: " + progress2);
        assertThat(progress2).startsWith("2 /");

        // --- Back button: returns to 1 / N ---
        WebElement backBtn = driver.findElement(By.id("qa-back"));
        System.out.println("[TEST] qa-back disabled before click: " + backBtn.getAttribute("disabled"));
        backBtn.click();
        Thread.sleep(600);
        String afterBack = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('qa-progress').textContent;");
        String qaIndex = (String) ((JavascriptExecutor) driver).executeScript(
                "return window._qa ? String(window._qa.index) : 'null';");
        System.out.println("[TEST] after back: progress=" + afterBack + " qa.index=" + qaIndex);
        assertThat(afterBack).startsWith("1 /");
        assertThat(driver.findElement(By.id("qa-back")).getAttribute("disabled")).isNotNull();

        // --- Member 1 again: PRESENT, advance to 2 / N ---
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
        Thread.sleep(600);
        String progress3 = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('qa-progress').textContent;");
        System.out.println("[TEST] progress after second save: " + progress3);
        assertThat(progress3).startsWith("2 /");

        // --- Member 2: PRESENT, advance to last (3 / N) ---
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
        Thread.sleep(600);
        String progress4 = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('qa-progress').textContent;");
        System.out.println("[TEST] progress after third save: " + progress4);
        assertThat(progress4).startsWith("3 /");

        // --- Member 3 (last): click PRESENT -> modal closes + success toast ---
        driver.findElement(By.cssSelector(".qa-status[data-status='PRESENT']")).click();
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('quick-attendance-modal').open === false;"));
        assertThat(isModalOpen()).isFalse();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.id("toast-container"), "Zapisano obecność"));

        // --- Reload and assert both members persisted as PRESENT ---
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content .status-select")));
        List<WebElement> selects = driver.findElements(By.cssSelector("#rehearsals-content .status-select"));
        for (WebElement sel : selects) {
            String val = (String) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].value;", sel);
            assertThat(val).as("Member status should persist as PRESENT after quick attendance").isEqualTo("PRESENT");
        }
        System.out.println("[TEST] all " + selects.size() + " members persisted as PRESENT");
    }

    private boolean isModalOpen() {
        return (Boolean) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('quick-attendance-modal').open === true;");
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
