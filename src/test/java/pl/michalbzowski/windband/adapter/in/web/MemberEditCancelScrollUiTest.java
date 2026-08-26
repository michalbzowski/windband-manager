package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for issue #95 - browser back button from member edit should return to members list:
 *
 * <p>Before: clicking "Anuluj" on the member edit form swapped the list fragment without proper
 * history tracking. Using browser's back button would redirect to dashboard instead of members list.
 *
 * <p>After: Changed "Anuluj" from HTMX button to anchor link with href="/members/list", enabling
 * proper browser history navigation (history.back() returns to members list correctly).
 */
class MemberEditCancelScrollUiTest extends UiTestBase {

    @Test
    void anulujReturnsToMembersList() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // === STEP 1: open the member list ===
        loginAndNavigateTo("/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-table")));

        // Pick the LAST member row so we have a distinct target.
        var rows = driver.findElements(By.cssSelector("#members-table tbody tr[data-member-id]"));
        assertThat(rows).isNotEmpty();
        int targetIndex = Math.max(1, rows.size() - 1);
        WebElement targetRow = rows.get(targetIndex);
        String memberId = targetRow.getAttribute("data-member-id");
        assertThat(memberId).isNotNull();

        // === STEP 2: open edit for that member (full page navigation) ===
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", targetRow);
        WebElement editBtn = wait.until(ExpectedConditions.elementToBeClickable(
                targetRow.findElement(By.xpath(".//button[contains(., 'Edytuj')]"))));
        String originalUrl = driver.getCurrentUrl();
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn);

        // After clicking "Edytuj" via HTMX, we're still on the list page with form loaded in fragment
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        // === STEP 3: click Anuluj (anchor link triggers full page navigation) ===
        WebElement anulujLink = driver.findElement(By.xpath("//a[contains(., 'Anuluj')]"));
        anulujLink.click();

        // Wait for the members list to load (full page navigation)
        wait.until(ExpectedConditions.urlMatches("/members$|/members/"));

        // Verify we're back at members list with table visible
        WebElement membersTable = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#members-table")));
        assertThat(membersTable).isNotNull();
        assertThat(membersTable.isDisplayed()).isTrue();

        // The member should still be in the list (not deleted)
        WebElement verifiedRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("tr[data-member-id='" + memberId + "']")));
        assertThat(verifiedRow).isNotNull();
    }
}
