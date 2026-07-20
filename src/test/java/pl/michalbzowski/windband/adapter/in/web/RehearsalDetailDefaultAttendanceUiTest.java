package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default attendance state for a freshly created rehearsal.
 *
 * <p>After creating a rehearsal there are NO attendance records in the DB, so every
 * member's status select in the detail view MUST default to NO_RESPONSE — never PRESENT.
 * This guards against the regression where a new rehearsal showed all members as PRESENT.
 */
class RehearsalDetailDefaultAttendanceUiTest extends UiTestBase {

    @Test
    void newRehearsal_showsAllMembersAsNoResponse_notPresent() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));

        // Create a rehearsal via the REST API (deterministic id, avoids the
        // multi-step UI form which is exercised by AttendancePersistenceUiTest).
        String today = java.time.LocalDate.now().toString();
        String rehearsalIdStr = (String) ((JavascriptExecutor) driver).executeScript(
                "return fetch('/api/rehearsals', {" +
                "  method: 'POST', headers: {'Content-Type':'application/json'}," +
                "  body: JSON.stringify({date: '" + today + "', startTime: '18:00'," +
                "    endTime: '20:00', location: 'Sala prób', bandId: 1})" +
                "}).then(r => r.json()).then(r => '' + r.id);");
        Thread.sleep(1000);
        Long rehearsalId = rehearsalIdStr != null ? Long.valueOf(rehearsalIdStr) : null;
        System.out.println("[TEST] created rehearsal id=" + rehearsalId);
        assertThat(rehearsalId).as("a rehearsal should have been created via API").isNotNull();

        // Open the detail view of the newly created rehearsal directly.
        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("select[id^='status_']")));

        // Verify every member's status select defaults to NO_RESPONSE
        List<org.openqa.selenium.WebElement> selects = driver.findElements(
                By.cssSelector("select[id^='status_']"));
        assertThat(selects)
                .as("There should be at least one member status select")
                .isNotEmpty();

        for (var select : selects) {
            String id = select.getAttribute("id");
            String value = select.getAttribute("value");
            boolean noRespSelected = select.findElement(
                    By.cssSelector("option[value='NO_RESPONSE']")).isSelected();
            System.out.println("[TEST] member select " + id + " value=" + value
                    + " noResponseOptionSelected=" + noRespSelected);
            assertThat(noRespSelected)
                    .as("Fresh rehearsal: member %s must default to NO_RESPONSE, not PRESENT", id)
                    .isTrue();
        }
    }
}
