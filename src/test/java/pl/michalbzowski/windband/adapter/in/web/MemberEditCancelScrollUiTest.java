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
 * Regression test for the "cancel edit → lost scroll position" issue:
 *
 * <p>Before the fix: clicking "Anuluj" on the member edit form swapped the list
 * fragment back in without any focus info, so the user landed at the bottom of
 * the list instead of the row they were editing.
 *
 * <p>After the fix: the cancel button issues {@code GET /members/list?focus={id}}
 * and the list fragment renders each row with {@code id="member-{id}"} plus a
 * script that scrolls the focused row into view and highlights it.
 */
class MemberEditCancelScrollUiTest extends UiTestBase {

    @Test
    void cancelEditReturnsToListFocusedOnEditedMember() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // === STEP 1: open the member list ===
        loginAndNavigateTo("/members");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-table")));

        // Pick the LAST member row so we are not at the top of the list.
        var rows = driver.findElements(By.cssSelector("#members-table tbody tr[data-member-id]"));
        assertThat(rows).isNotEmpty();
        // Use a row that is NOT the first one (to prove we can scroll back to it).
        int targetIndex = Math.max(1, rows.size() - 1);
        WebElement targetRow = rows.get(targetIndex);
        String memberId = targetRow.getAttribute("data-member-id");
        assertThat(memberId).isNotNull();
        String expectedId = "member-" + memberId;

        // === STEP 2: open edit for that member ===
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", targetRow);
        WebElement editBtn = wait.until(ExpectedConditions.elementToBeClickable(
                targetRow.findElement(By.xpath(".//button[contains(text(), 'Edytuj')]"))));
        // Click via JS to avoid ElementClickIntercepted (sticky nav / overlay in full suite)
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", editBtn);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        // === STEP 3: scroll the form to the top so we can prove we come back to the row ===
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, 0);");

        // === STEP 4: click Anuluj ===
        driver.findElement(By.xpath("//button[contains(text(), 'Anuluj')]")).click();

        // === STEP 5: list is back, and the focused row carries id="member-{id}" ===
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-table")));
        WebElement focusedRow = wait.until(
                ExpectedConditions.presenceOfElementLocated(By.id(expectedId)));
        assertThat(focusedRow).isNotNull();
        // The focused row must be the one we edited (data-member-id matches).
        assertThat(focusedRow.getAttribute("data-member-id")).isEqualTo(memberId);
        // It must be visible in the viewport (scrollIntoView ran).
        assertThat(focusedRow.isDisplayed()).isTrue();

        // Real-flow proof: the list fragment was rendered WITH the focus param
        // (HTMX fragment swap does not change the browser URL, so we assert on
        // the data-focus-id attribute the controller injected into the NEW fragment).
        WebElement focusedContent = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#members-content[data-focus-id='" + memberId + "']")));
        assertThat(focusedContent).isNotNull();
    }
}
