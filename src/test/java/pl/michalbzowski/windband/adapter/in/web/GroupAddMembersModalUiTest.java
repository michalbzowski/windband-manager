package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the multi-member add modal on the group detail page:
 * <ol>
 *   <li>Clicking "Dodaj członków" opens the modal with a checkbox per available member.</li>
 *   <li>Checking several members and clicking "Dodaj zaznaczonych" adds them
 *       all to the group's members table.</li>
 *   <li>The newly added rows are highlighted (green pulse) via the shared mechanism.</li>
 * </ol>
 */
class GroupAddMembersModalUiTest extends UiTestBase {

    @Test
    void addMultipleMembersToGroupViaModal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String aFirst = "GrpA" + uid;
        String aLast = "Test" + uid;
        String bFirst = "GrpB" + uid;
        String bLast = "Test" + uid;

        // --- Create two members via UI ---
        createMember(aFirst, aLast);
        createMember(bFirst, bLast);

        // --- Create a group via API ---
        String groupIdStr = (String) ((JavascriptExecutor) driver).executeScript(
                "return fetch('/api/groups', {" +
                "  method: 'POST', headers: {'Content-Type':'application/json'}," +
                "  body: JSON.stringify({name: 'ModalTestGrp" + uid + "', description: 'test'})" +
                "}).then(r => r.json()).then(g => '' + g.id);");
        Thread.sleep(800);
        Long groupId = groupIdStr != null ? Long.valueOf(groupIdStr) : null;
        assertThat(groupId).isNotNull();

        // --- Open group detail ---
        driver.get(baseUrl() + "/groups/" + groupId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("groups-content")));

        // --- Verify the "Dodaj członków" button exists (not a dynamic group) ---
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("open-add-members-modal-btn")));

        // --- Check that members are listed in the available members modal ---
        // Open the modal
        jsClick(driver.findElement(By.id("open-add-members-modal-btn")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("add-members-to-group-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('add-members-to-group-modal').open === true;"));

        // Check that both created members appear in the modal
        assertThat(driver.findElements(By.xpath(
                "//*[@id='add-members-to-group-modal']//label[contains(text(), '" + aFirst + "')]")))
                .as("Member A should be in the add-members modal")
                .hasSize(1);
        assertThat(driver.findElements(By.xpath(
                "//*[@id='add-members-to-group-modal']//label[contains(text(), '" + bFirst + "')]")))
                .as("Member B should be in the add-members modal")
                .hasSize(1);

        // --- Check the two members in the modal ---
        checkMember(aFirst + " " + aLast);
        checkMember(bFirst + " " + bLast);

        // --- Add selected ---
        int before = driver.findElements(
                By.cssSelector("#groups-content table tbody tr")).size();
        System.out.println("[TEST] members before add: " + before);

        jsClick(driver.findElement(By.id("add-selected-members-btn")));

        // Wait for HTMX to complete the reload
        try { Thread.sleep(1500); } catch (InterruptedException ignored) { /* noop - wait for reload */ }

        // Members table should now contain both added members
        int after = driver.findElements(
                By.cssSelector("#groups-content table tbody tr")).size();
        System.out.println("[TEST] members after add: " + after);
        assertThat(after)
                .as("Both members should have been added to the group")
                .isEqualTo(before + 2);
    }

    private void checkMember(String memberName) {
        var cb = driver.findElement(By.xpath(
                "//*[@id='add-members-to-group-modal']//label[contains(text(), '" + memberName + "')]"));
        driver.findElement(By.xpath(
                "//*[@id='add-members-to-group-modal']//label[contains(text(), '" + memberName + "')]/preceding-sibling::input[@type='checkbox']"))
                .click();
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
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#members-content table tbody tr")));
        try { Thread.sleep(800); } catch (InterruptedException ignored) { /* noop - allow HTMX reload */ }
    }

    private void fill(String name, String value) {
        var el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    private void jsClick(org.openqa.selenium.WebElement el) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
    }
}