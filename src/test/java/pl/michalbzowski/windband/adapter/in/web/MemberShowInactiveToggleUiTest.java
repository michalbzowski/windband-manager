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
 * Regression test for Issue #117: 'Pokaż nieaktywnych' button not working.
 * <p>The bug occurred when clicking the toggle button to show/hide inactive members.
 * The JavaScript would send an empty/null focus parameter which caused Spring's
 * type converter to throw: "Failed to convert value of type java.lang.String to
 * required type java.lang.Long; For input string: null"
 * <p>This test verifies that the toggle button works correctly and inactive members
 * can be displayed without conversion errors.
 */
class MemberShowInactiveToggleUiTest extends UiTestBase {

    @Test
    void togglingInactiveMembers_worksWithoutConversionError() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Login and navigate to members page
        loginAndNavigateTo("/members");

        // Wait for the toggle button
        WebElement toggleButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Pokaż nieaktywnych') or contains(@id, 'toggleMembers')]")));

        assertThat(toggleButton).isNotNull();
        System.out.println("[TEST] Found toggle button: " + toggleButton.getText());

        // Click the "Pokaż nieaktywnych" button
        toggleButton.click();

        // Wait for the HTMX request to complete and page to update
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//span[contains(text(), 'Nieaktywni')]")));

        // Verify we're now seeing inactive members section
        WebElement contentDiv = driver.findElement(By.id("members-content"));
        boolean showingInactive = Boolean.parseBoolean(contentDiv.getAttribute("data-show-inactive"));

        assertThat(showingInactive).as("Should be showing inactive members").isTrue();
        System.out.println("[TEST] Successfully toggled to show inactive members");
    }

    @Test
    void togglingBackToActiveMembers_works() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Login and navigate directly with showInactive=true
        loginAndNavigateTo("/members?showInactive=true");

        // Verify we're showing inactive members initially
        WebElement contentDiv = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("members-content")));
        boolean initialState = Boolean.parseBoolean(contentDiv.getAttribute("data-show-inactive"));
        assertThat(initialState).as("Should start showing inactive members").isTrue();

        // Find and click the toggle button (now should show "Pokaż aktywnych")
        WebElement toggleButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'aktywnych') or contains(@id, 'toggleMembers')]")));

        assertThat(toggleButton).isNotNull();
        System.out.println("[TEST] Found toggle button: " + toggleButton.getText());

        // Click to toggle back to active members
        toggleButton.click();

        // Wait for HTMX request and verify state change
        wait.until(ExpectedConditions.attributeToBe(By.id("members-content"), "data-show-inactive", "false"));

        boolean newState = Boolean.parseBoolean(contentDiv.getAttribute("data-show-inactive"));
        assertThat(newState).as("Should toggle back to showing active members").isFalse();
        System.out.println("[TEST] Successfully toggled back to show active members");
    }
}
