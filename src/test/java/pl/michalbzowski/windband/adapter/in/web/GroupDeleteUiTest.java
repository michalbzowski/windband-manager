package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces and verifies the fix for issue #81:
 * "Nie da się usunąć grupy" — clicking "Usuń grupę" returns 404.
 *
 * <p>The root cause was that the form used {@code hx-post} (no {@code th:} prefix),
 * so Thymeleaf did not process the expression. The POST was sent to the literal
 * URL {@code @{/groups/{id}/delete(id=${group.id})}} which does not exist.
 *
 * <p>After the fix (using {@code th:hx-post}), the form action is resolved to the
 * correct endpoint. This test verifies:
 * <ol>
 *   <li>Navigating to the group detail page</li>
 *   <li>Clicking the "Usuń grupę" button triggers a confirmation dialog</li>
 *   <li>Accepting the confirmation causes a redirect to the groups list</li>
 *   <li>The deleted group no longer appears on the list</li>
 * </ol>
 */
class GroupDeleteUiTest extends UiTestBase {

    @Test
    void shouldDeleteGroup_afterConfirmingAlert() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Navigate to groups list first to find a group
        loginAndNavigateTo("/groups");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("groups-content")));

        // The page should show at least one group; click into the first group's detail
        var groupLinks = driver.findElements(By.cssSelector("#groups-content a[href*='/groups/']"));
        if (groupLinks.isEmpty()) {
            // If there are direct links, try finding table rows or cards with group names
            groupLinks = driver.findElements(By.xpath("//a[contains(@href, '/groups/')]"));
        }

        if (groupLinks.isEmpty()) {
            // No groups exist — seed one by going to the new group form
            driver.get(baseUrl() + "/groups/new");
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form input[name='name']")));
            driver.findElement(By.cssSelector("input[name='name']")).sendKeys("TestDeleteGroup");
            driver.findElement(By.cssSelector("button[type='submit'].primary")).click();
            wait.until(ExpectedConditions.urlContains("/groups/"));
        } else {
            groupLinks.get(0).click();
            wait.until(ExpectedConditions.urlContains("/groups/"));
        }

        // We are now on a group detail page
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("groups-content")));

        // Verify the "Usuń grupę" button exists
        var deleteBtn = driver.findElement(
                By.cssSelector("form[hx-target='#content'] button[type='submit']"));
        assertThat(deleteBtn.getText()).contains("Usuń grupę");

        // Get the group name before deletion for later verification
        String groupName = driver.findElement(By.cssSelector("#groups-content h2 span")).getText();

        // Click the delete button — this triggers a JS confirm() dialog
        deleteBtn.click();

        // Handle the confirm dialog — accept it
        wait.until(ExpectedConditions.alertIsPresent());
        driver.switchTo().alert().accept();

        // After accepting, the page should redirect to the groups list
        wait.until(ExpectedConditions.urlContains("/groups"));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("groups-content")));

        // Verify the deleted group is no longer in the list
        String listContent = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.querySelector('#groups-content').textContent;");
        assertThat(listContent)
                .as("Deleted group '%s' should not appear in the groups list", groupName)
                .doesNotContain(groupName);
    }
}
