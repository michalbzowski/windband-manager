package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the multi-member invite modal on the event detail page:
 * <ol>
 *   <li>Clicking "Zaproś uczestników" opens the modal with a checkbox per member.</li>
 *   <li>Checking several members and clicking "Zaproś zaznaczone osoby" adds them
 *       all to the event's participants table.</li>
 *   <li>The newly invited rows are highlighted (green pulse) via the shared mechanism.</li>
 * </ol>
 */
class EventInviteModalUiTest extends UiTestBase {

    @Test
    void inviteMultipleMembersViaModal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String aFirst = "InvA" + uid;
        String aLast = "Test" + uid;
        String bFirst = "InvB" + uid;
        String bLast = "Test" + uid;

        // --- Create two members via UI ---
        createMember(aFirst, aLast);
        createMember(bFirst, bLast);

        // --- Create an event (band_events are truncated per test, so list starts empty) ---
        loginAndNavigateTo("/events");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj wydarzenie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#event-form")));
        String eventName = "InviteEvt" + uid;
        fill("name", eventName);
        String today = java.time.LocalDate.now().toString();
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + today + "';");
        driver.findElement(By.cssSelector("#event-form button[type='submit'].primary")).click();
        // The form does a full navigation to /events?focus=<id>; wait for the list to
        // reload with the new event (the Szczegóły button only exists on the list view).
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//button[contains(text(), 'Szczegóły')]")));

        // --- Open event detail (JS click: avoid overlay/scroll interception in full suite) ---
        jsClick(driver.findElement(By.xpath("//button[contains(text(), 'Szczegóły')]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-invite-modal-btn")));

        // --- Open the invite modal (JS click: the button sits under the sticky top nav) ---
        jsClick(driver.findElement(By.id("open-invite-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("invite-members-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invite-members-modal').open === true;"));

        // --- Check the two created members in the modal ---
        checkMember(aFirst + " " + aLast);
        checkMember(bFirst + " " + bLast);

        int before = driver.findElements(
                By.cssSelector("#participants-table tbody tr")).size();
        System.out.println("[TEST] participants before invite: " + before);

        // --- Invite selected ---
        jsClick(driver.findElement(By.id("invite-selected-btn")));

        // Participants table should now contain both invited members
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='participants-table']//tr[.//td[contains(text(), '" + aFirst + "')]]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='participants-table']//tr[.//td[contains(text(), '" + bFirst + "')]]")));

        // At least one newly invited row should be highlighted (green pulse)
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#participants-table tbody tr.highlight-row")));

        int after = driver.findElements(
                By.cssSelector("#participants-table tbody tr")).size();
        System.out.println("[TEST] participants after invite: " + after);
        assertThat(after).isEqualTo(before + 2);
    }

    private void createMember(String first, String last) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", first);
        fill("lastName", last);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        // Wait for the create form to close (post-submit settle — the modal is the indicator
        // the request has been processed and the page is ready for the next member)
        wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector("#member-form")));
    }

    private void checkMember(String fullName) {
        WebElement li = driver.findElement(
                By.xpath("//li[.//label[contains(text(), '" + fullName + "')]]"));
        WebElement cb = li.findElement(By.cssSelector("input.invite-checkbox"));
        cb.click();
    }

    private void jsClick(WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }
}
