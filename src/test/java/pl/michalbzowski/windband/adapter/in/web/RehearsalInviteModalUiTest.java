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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the rehearsal detail page's unified invite flow (t_7e21ac5b):
 * <ul>
 *     <li>exactly ONE "Zaproś" button (#open-invite-btn) is rendered in the invite section</li>
 *     <li>the legacy two-button UI ("Zaproś uczestników" / "Zaproś grupę") and the old
 *         server-rendered #invite-members-modal / #invite-group-modal dialogs are gone</li>
 *     <li>clicking it opens the shared InvitationModal (#invitation-unified-modal)</li>
 *     <li>picking a member row and confirming sends POST /api/rehearsals/{id}/invite and
 *         produces an attendance row for that member on the detail page.</li>
 * </ul>
 */
class RehearsalInviteModalUiTest extends UiTestBase {

    @Test
    void shouldOpenUnifiedInviteModalAndInviteMemberToRehearsal() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        String uid = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "RehInv" + uid;
        String lastName = "Member" + uid;
        String fullName = firstName + " " + lastName;

        createMember(firstName, lastName, wait);

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]")).click();
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

        // Exactly one unified invite button in the invite section (acceptance #2),
        // and no legacy two-button UI / old server-rendered dialogs anywhere (AC#5).
        WebElement inviteBtn = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("open-invite-btn")));
        assertThat(driver.findElements(By.cssSelector(".rehearsal-invite-actions button")))
                .hasSize(1);
        assertThat(driver.findElements(By.id("open-invite-modal-btn"))).isEmpty();
        assertThat(driver.findElements(By.id("open-invite-group-modal-btn"))).isEmpty();
        assertThat(driver.findElements(By.id("invite-members-modal"))).isEmpty();
        assertThat(driver.findElements(By.id("invite-group-modal"))).isEmpty();

        // Click "Zaproś" — the shared InvitationModal must open (acceptance #3).
        jsClick(inviteBtn);
        WebElement dlg = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("invitation-unified-modal")));
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "return document.getElementById('invitation-unified-modal').open === true;"));

        // Select the newly created member row. Rows are <button class="invitation-row"
        // data-kind="member" data-id="N"> — click via a fresh document.querySelector so we
        // never hold a stale element reference across the re-render (same pattern as
        // EventInviteGroupSecondEventUiTest.clickRowAndConfirm).
        String escapedName = fullName.replace("\\", "\\\\").replace("'", "''");
        ((JavascriptExecutor) driver).executeScript(
                "var rows = Array.prototype.slice.call(" +
                "document.querySelectorAll(\".invitation-row[data-kind='member']\"));" +
                "var row = rows.find(function(r){ return r.textContent.indexOf('" + escapedName + "') !== -1; });" +
                "if (!row) throw new Error('member row not found in unified invite modal: " + escapedName + "');" +
                "row.click();");
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript(
                "var rows = Array.prototype.slice.call(" +
                "document.querySelectorAll(\".invitation-row[data-kind='member']\"));" +
                "return rows.some(function(r){ return r.getAttribute('aria-checked') === 'true'; });"));

        // Confirm — the modal hands back the deduplicated member id list, the page
        // POSTs it to /api/rehearsals/{id}/invite and refreshes #rehearsals-content.
        // Re-resolve the button at click time: every row toggle re-renders the dialog,
        // so a WebElement resolved earlier is stale by then.
        wait.until(d -> d.findElements(By.cssSelector("button.invitation-confirm")).stream()
                .filter(WebElement::isEnabled).findFirst().orElse(null) != null);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"button.invitation-confirm\").click();");

        wait.until(d -> {
            try {
                return (Boolean) ((JavascriptExecutor) d).executeScript(
                        "return !document.getElementById('invitation-unified-modal') ||" +
                        "       document.getElementById('invitation-unified-modal').open === false;");
            } catch (Exception e) {
                return true;
            }
        });

        // The invitation appears in the attendance list.
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='rehearsals-content']//select[@data-member-id and .//option[@value='NO_RESPONSE']]")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(
                "//*[@id='rehearsals-content']//tr[.//td[contains(., '" + fullName + "')]]")));

        List<WebElement> memberRows = driver.findElements(By.xpath(
                "//*[@id='rehearsals-content']//tr[.//td[contains(., '" + fullName + "')]]"));
        assertThat(memberRows).isNotEmpty();
    }

    private void createMember(String firstName, String lastName, WebDriverWait wait) {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj członka')]")).click();
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
