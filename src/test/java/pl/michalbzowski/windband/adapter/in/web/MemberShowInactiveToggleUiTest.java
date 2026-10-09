package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #117 regression, full browser path: on the members list (no member
 * focused — {@code data-focus-id} empty) clicking "Pokaż nieaktywnych" fired
 * {@code GET /members/list?showInactive=true&focus=null}; Spring rejected the
 * literal "null" for the {@code Long focus} parameter and the HTMX swap was
 * aborted, so the button visibly did nothing (server-side proof in
 * {@link MemberListToggleNullFocusControllerTest}).
 *
 * <p>After the fix the click must flip the container into inactive mode and the
 * seeded inactive row must render.</p>
 */
class MemberShowInactiveToggleUiTest extends UiTestBase {

    @Test
    void showInactiveButton_swapsToInactiveList_withoutFocusedMember() throws Exception {
        String uid = Long.toHexString(System.nanoTime());
        String lastName = "Wykluta" + uid;
        // Seed one INACTIVE member in the test band so the post-toggle list has a deterministic row.
        jdbcTemplate.update(
                "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                "VALUES (?, ?, '1988-01-02', false, CURRENT_DATE, false, 1)",
                "Zosia" + uid, lastName);

        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toggleMembersBtn")));

        // Precondition: active list shown, no (broken) "null" focus state.
        WebElement contentDiv = driver.findElement(By.id("members-content"));
        assertThat(contentDiv.getAttribute("data-show-inactive")).isEqualTo("false");
        assertThat(contentDiv.getAttribute("data-focus-id")).isNotEqualTo("null");

        // THE user's exact action from the issue report.
        driver.findElement(By.id("toggleMembersBtn")).click();

        // 1) The swap must actually happen — before the fix the request 500'd and nothing updated.
        wait.until(d -> "true".equals(((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                "return document.getElementById('members-content').getAttribute('data-show-inactive');")));

        // 2) The newly-shown list must contain the seeded inactive member.
        //    Re-query fresh on every poll: the HTMX swap replaces #members-content, invalidating stale references.
        wait.until(d -> {
            try {
                return d.findElements(By.cssSelector("#members-content tr[data-member-id]")).stream()
                        .anyMatch(r -> r.getText().contains(lastName));
            } catch (org.openqa.selenium.StaleElementReferenceException e) {
                return false; // mid-swap — retry on the next poll
            }
        });
        System.out.println("[TEST] seeded inactive row present after toggle: true");

        // 3) Toggle back and verify the round trip keeps working (state machine, both directions).
        driver.findElement(By.id("toggleMembersBtn")).click();
        wait.until(d -> "false".equals(((org.openqa.selenium.JavascriptExecutor) d).executeScript(
                "return document.getElementById('members-content').getAttribute('data-show-inactive');")));
    }
}
