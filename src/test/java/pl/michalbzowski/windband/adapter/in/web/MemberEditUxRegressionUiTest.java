package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the member edit UX issue:
 *
 * <p>Before the fix: editing a member with 5 custom attributes triggered 6 toasts
 * (1 "Zapisano członka" + 5 "Zapisano atrybut"), and after save the list scrolled
 * to the bottom with no visual feedback for the edited row.
 *
 * <p>After the fix:
 * <ul>
 *   <li>Only 1 success toast appears ("Zapisano członka")</li>
 *   <li>Edited member row has the {@code highlight-row} class for 3 seconds</li>
 *   <li>Each row has a {@code data-member-id} attribute for JS targeting</li>
 *   <li>The page scrolls to the edited member row</li>
 * </ul>
 */
class MemberEditUxRegressionUiTest extends UiTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private MemberRepository memberRepository;

    @Test
    void shouldShowOnlyOneSuccessToastAndHighlightEditedRow() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "UxTest" + unique;
        String lastName = "UxLast" + unique;
        String dob = "1990-01-15";
        String updatedDob = "1995-06-20";
        String email = "uxtest" + unique + "@test.pl";
        String updatedEmail = "uxtest.updated" + unique + "@test.pl";

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        Long memberId = null;

        try {
            // === STEP 1: Create a member ===
            loginAndNavigateTo("/members");
            driver.findElement(By.xpath("//button[contains(text(), 'Dodaj członka')]")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            fillField("firstName", firstName);
            fillField("lastName", lastName);
            fillField("dateOfBirth", dob);
            fillField("email", email);
            submitPrimaryFormButton();

            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));

            memberId = readMemberIdFromEditButton(wait, firstName + " " + lastName);

            // === STEP 2: Verify table structure — data-member-id on rows + table id ===
            WebElement ourRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("tr[data-member-id='" + memberId + "']")));
            assertThat(ourRow.getAttribute("data-member-id"))
                    .as("Member row should have data-member-id attribute for JS targeting")
                    .isEqualTo(String.valueOf(memberId));

            // Wait for the add-member success toast to auto-hide (3s + 300ms animation)
            // so the test only counts the toasts from the EDIT operation.
            try { Thread.sleep(3500); } catch (InterruptedException ignored) { /* intentionally ignored */ }

            // === STEP 3: Open edit form ===
            clickEditForMember(wait, firstName + " " + lastName);
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

            // === STEP 4: Change one field, submit ===
            clearAndFillField("email", updatedEmail);
            submitPrimaryFormButton();

            // === STEP 5: Wait for the list to reload and the highlight to be applied ===
            // The afterSettle handler runs 50ms after swap settles, so we give it a moment.
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), updatedEmail));

            // Wait until the highlight class appears (it's added ~50ms after swap settles)
            final long expectedId = memberId;
            new WebDriverWait(driver, Duration.ofSeconds(2)).until(driver -> {
                Object has = ((JavascriptExecutor) driver).executeScript(
                        "var r = document.querySelector(\"tr[data-member-id='" + expectedId + "']\");"
                                + "return r ? r.classList.contains('highlight-row') : false;");
                return Boolean.TRUE.equals(has);
            });

            // === STEP 6: Verify the edited row has highlight-row class ===
            Object hasHighlight = ((JavascriptExecutor) driver).executeScript(
                    "var r = document.querySelector(\"tr[data-member-id='" + expectedId + "']\");"
                            + "return r ? r.classList.contains('highlight-row') : false;");
            assertThat(hasHighlight)
                    .as("Edited member row should have 'highlight-row' class for 3s feedback")
                    .isEqualTo(true);

            // === STEP 7: Verify only ONE success toast (not 6) ===
            // Wait briefly to let all parallel fetches complete + give the success toast time to render
            try { Thread.sleep(800); } catch (InterruptedException ignored) { /* intentionally ignored */ }

            // The previous 'Dodaj członka' toast has long auto-hid (3s+). Only the edit toast
            // should be visible. Verify NO per-attribute toasts (the ones we consolidated away).
            String allToastText = (String) ((JavascriptExecutor) driver).executeScript(
                    "return document.getElementById('toast-container') ? document.getElementById('toast-container').textContent : '';");
            assertThat(allToastText)
                    .as("No per-attribute 'Zapisano atrybut' toasts should appear after member edit")
                    .doesNotContain("Zapisano atrybut");

            // Verify at most 1 success toast (the 'Zapisano członka' from the edit)
            int successToastCount = ((Long) ((JavascriptExecutor) driver).executeScript(
                    "return document.querySelectorAll('#toast-container .toast.success').length;")).intValue();
            assertThat(successToastCount)
                    .as("After editing a member, at most 1 success toast should be visible (the edit one)")
                    .isLessThanOrEqualTo(1);

            // Verify the success toast we DO see is the 'Zapisano członka' from the edit
            String editToastText = (String) ((JavascriptExecutor) driver).executeScript(
                    "var t = document.querySelector('#toast-container .toast.success'); return t ? t.textContent : '';");
            assertThat(editToastText)
                    .as("The visible success toast should be 'Zapisano członka' (the edit confirmation)")
                    .containsIgnoringCase("Zapisano członka");

            // === STEP 8: Verify the highlight disappears after ~3 seconds ===
            try { Thread.sleep(3500); } catch (InterruptedException ignored) { /* intentionally ignored */ }
            Object stillHasHighlight = ((JavascriptExecutor) driver).executeScript(
                    "var r = document.querySelector(\"tr[data-member-id='" + expectedId + "']\");"
                            + "return r ? r.classList.contains('highlight-row') : false;");
            assertThat(stillHasHighlight)
                    .as("Highlight-row class should be removed after ~3 seconds")
                    .isEqualTo(false);

        } finally {
            // Cleanup
            if (memberId != null) {
                deleteMemberViaApi(memberId);
            } else {
                memberRepository.findAllActive().stream()
                        .filter(m -> firstName.equals(m.getFirstName()))
                        .forEach(m -> deleteMemberViaApi(m.getId()));
            }
        }
    }

    // --- Helpers (kept in sync with MemberUiTest patterns) ---

    private void fillField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        if ("date".equals(input.getAttribute("type"))) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = '" + value + "';", input);
        } else {
            input.sendKeys(value);
        }
    }

    private void clearAndFillField(String name, String value) {
        WebElement input = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        input.clear();
        if ("date".equals(input.getAttribute("type"))) {
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].value = '" + value + "';", input);
        } else {
            input.sendKeys(value);
        }
    }

    private void submitPrimaryFormButton() {
        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();
    }

    private void clickEditForMember(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(text(), 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", btn);
    }

    private Long readMemberIdFromEditButton(WebDriverWait wait, String fullName) {
        String xpath = String.format(
                "//tr[td[contains(., '%s')]]//button[contains(text(), 'Edytuj')]", fullName);
        WebElement btn = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(xpath)));
        String hxGet = btn.getAttribute("hx-get");
        if (hxGet == null) {
            throw new IllegalStateException("'Edytuj' button has no hx-get: " + btn.getAttribute("outerHTML"));
        }
        return Long.parseLong(hxGet.split("/")[2]);
    }

    @SuppressWarnings("unchecked")
    private boolean deleteMemberViaApi(Long id) {
        String script = ""
                + "var done = arguments[0];"
                + "fetch('/api/members/" + id + "', {method: 'DELETE', credentials: 'same-origin'})"
                + "  .then(function(r) { done(r.status); })"
                + "  .catch(function(e) { done(0); });";
        Object status = ((JavascriptExecutor) driver).executeAsyncScript(script);
        return status instanceof Number && ((Number) status).intValue() == 204;
    }
}
