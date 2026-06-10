package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI tests for the toast notification system.
 *
 * <p>Verifies that toasts:
 * <ul>
 *   <li>Are appended to {@code #toast-container} (the container, defined as a
 *       Thymeleaf fragment and included by every full page)</li>
 *   <li>Have the correct text and CSS class ({@code .toast.success / .error / .info})</li>
 *   <li>Are visible: container has {@code position: fixed} at bottom-right,
 *       toast is within the viewport, not hidden, non-zero size</li>
 *   <li>Are auto-removed after the success/info timeout (~3-5s); error toasts stay until dismissed</li>
 * </ul>
 *
 * <p>These tests were added in response to a user report that they could not
 * see the success toast after adding a member. Investigation showed two
 * separate problems:
 * <ol>
 *   <li>The {@code <div id="toast-container">} was sitting in
 *       {@code fragments/layout.html} as a bare element, not wrapped in a
 *       Thymeleaf fragment. No page was including it, so it was never
 *       rendered. Fix: wrap in a fragment and include it from every full
 *       page (15 files updated).</li>
 *   <li>The CSS {@code #toast-container { position: fixed; ... }} was correct
 *       and is now applied — the container is rendered at bottom-right of
 *       the viewport.</li>
 * </ol>
 * The tests below pin down the exact rendering so any future regression
 * (CSS conflict, z-index, off-screen, fragment inclusion missing, etc.) is
 * caught.</p>
 */
class ToastUiTest extends UiTestBase {

    /**
     * Direct API call: invoke {@code Toast.success} from the page context and
     * verify the toast is rendered, visible, and positioned where the CSS
     * spec says.
     */
    @Test
    void shouldDisplaySuccessToastWithCorrectPositionAndStyles() {
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Sanity: the container exists in the DOM (it must be included by the page)
        WebElement container = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.id("toast-container")));
        assertThat(container).as("#toast-container should be in the DOM").isNotNull();

        // === Check the CONTAINER's styles (this is what makes the toast appear at bottom-right) ===
        Map<String, Object> containerStyles = (Map<String, Object>) ((JavascriptExecutor) driver)
                .executeScript("" +
                        "var c = arguments[0];" +
                        "var cs = window.getComputedStyle(c);" +
                        "var r = c.getBoundingClientRect();" +
                        "return JSON.parse(JSON.stringify({" +
                        "  display: cs.display," +
                        "  position: cs.position," +
                        "  zIndex: cs.zIndex," +
                        "  bottom: cs.bottom," +
                        "  right: cs.right," +
                        "  containerWidth: r.width," +
                        "  containerHeight: r.height" +
                        "}));",
                        container);
        assertThat(containerStyles.get("display"))
                .as("Container display (should be 'flex' per CSS)").isEqualTo("flex");
        assertThat(containerStyles.get("position"))
                .as("Container position MUST be 'fixed' so the toast appears at viewport bottom-right")
                .isEqualTo("fixed");
        assertThat(Integer.parseInt(containerStyles.get("zIndex").toString()))
                .as("Container z-index should be high (>= 1000) so it's above page content")
                .isGreaterThanOrEqualTo(1000);

        // Show a success toast via the global API
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String message = "Test success " + unique;
        ((JavascriptExecutor) driver).executeScript("window.Toast.success('" + message + "');");

        // Wait for the toast to appear in the DOM
        WebElement toast = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#toast-container .toast.success")));
        // Wait for the slide-in animation (0.3s) so textContent is stable
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Use textContent via JS to avoid Selenium getText() quirks (it sometimes
        // returns "" for elements inside position:fixed containers with animations)
        String toastText = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.querySelector('#toast-container .toast.success').textContent;");
        assertThat(toastText)
                .as("Toast textContent")
                .contains(message);
        assertThat(toast.getAttribute("class"))
                .as("Toast CSS class")
                .contains("toast")
                .contains("success");

        // Wait for the slide-in animation to finish (0.3s) before checking positions
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // === Toast computed styles + position check ===
        Map<String, Object> styles = (Map<String, Object>) ((JavascriptExecutor) driver)
                .executeScript("" +
                        "var t = arguments[0];" +
                        "var cs = window.getComputedStyle(t);" +
                        "var r = t.getBoundingClientRect();" +
                        "var vw = window.innerWidth, vh = window.innerHeight;" +
                        "return JSON.parse(JSON.stringify({" +
                        "  display: cs.display," +
                        "  visibility: cs.visibility," +
                        "  opacity: cs.opacity," +
                        "  width: r.width," +
                        "  height: r.height," +
                        "  right: vw - r.right," +
                        "  bottom: vh - r.bottom," +
                        "  inViewport: r.right > 0 && r.bottom > 0 && r.left < vw && r.top < vh" +
                        "}));",
                        toast);

        assertThat(styles.get("display"))
                .as("Toast display should not be 'none'").isNotEqualTo("none");
        assertThat(styles.get("visibility"))
                .as("Toast visibility should be 'visible'").isEqualTo("visible");
        assertThat(Double.parseDouble(styles.get("opacity").toString()))
                .as("Toast opacity should be > 0 (animation should have completed)").isGreaterThan(0.0);

        double width = Double.parseDouble(styles.get("width").toString());
        double height = Double.parseDouble(styles.get("height").toString());
        assertThat(width).as("Toast width should be > 0").isGreaterThan(0.0);
        assertThat(height).as("Toast height should be > 0").isGreaterThan(0.0);

        assertThat(styles.get("inViewport"))
                .as("Toast should be within the viewport (i.e. user can see it)")
                .isEqualTo(true);

        // === Auto-dismissal: success toasts disappear after ~3s ===
        // Toast.show sets a 3s timeout for success; the fade-out animation is 0.3s.
        // We wait up to 8s for the element to be gone.
        new WebDriverWait(driver, Duration.ofSeconds(8))
                .until(ExpectedConditions.invisibilityOfElementLocated(
                        By.cssSelector("#toast-container .toast.success")));
    }

    /**
     * Direct API call for an ERROR toast: should stay visible (no auto-hide)
     * until explicitly dismissed.
     */
    @Test
    void shouldDisplayErrorToastThatDoesNotAutoHide() {
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        ((JavascriptExecutor) driver).executeScript(
                "window.Toast.error('Test error: cos poszlo nie tak');");

        // Wait for the toast AND the slide-in animation to finish
        WebElement toast = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#toast-container .toast.error")));
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Re-fetch the element after animation so getText() returns the rendered text
        toast = driver.findElement(By.cssSelector("#toast-container .toast.error"));
        assertThat(toast.getText())
                .as("Error toast should display the error message")
                .contains("Test error");
        assertThat(toast.getAttribute("class")).contains("error");

        // Wait 4s — error toasts do NOT auto-hide
        try { Thread.sleep(4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        List<WebElement> stillThere = driver.findElements(
                By.cssSelector("#toast-container .toast.error"));
        assertThat(stillThere)
                .as("Error toasts should NOT auto-hide")
                .isNotEmpty();
    }

    /**
     * Full form-flow test: the user adds a member through the UI, and the
     * success toast "Zapisano członka" must appear.
     */
    @Test
    void shouldShowSuccessToastAfterAddingMemberThroughForm() {
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Open the new-member form
        driver.findElement(By.xpath("//button[contains(text(), 'Dodaj muzyka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));

        // Fill required fields with a unique identity
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String firstName = "ToastTest" + unique;
        String lastName = "TestLast" + unique;
        driver.findElement(By.cssSelector("input[name='firstName']")).sendKeys(firstName);
        driver.findElement(By.cssSelector("input[name='lastName']")).sendKeys(lastName);
        driver.findElement(By.cssSelector("input[name='dateOfBirth']")).sendKeys("1990-05-15");

        // Submit via the primary button
        driver.findElement(By.cssSelector("form#member-form button.primary[type='submit']")).click();

        // The form's submit handler calls fetchWithToast({toastMessage: 'Zapisano członka'}).
        // That toast should appear in #toast-container .toast.success.
        WebElement toast = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#toast-container .toast.success")));
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // Re-fetch after animation for accurate text/visibility
        toast = driver.findElement(By.cssSelector("#toast-container .toast.success"));
        assertThat(toast.getText()).contains("Zapisano członka");

        // Verify it's actually visible (not just in the DOM)
        Object isVisible = ((JavascriptExecutor) driver).executeScript(
                "var t = arguments[0];" +
                "var r = t.getBoundingClientRect();" +
                "return r.width > 0 && r.height > 0 && r.right > 0 && r.bottom > 0" +
                "   && window.getComputedStyle(t).visibility === 'visible'" +
                "   && window.getComputedStyle(t).display !== 'none'" +
                "   && parseFloat(window.getComputedStyle(t).opacity) > 0;",
                toast);
        assertThat(isVisible).as("Success toast must be actually visible").isEqualTo(true);

        // Cleanup: delete the test member via fetch from the browser
        try {
            wait.until(ExpectedConditions.textToBePresentInElementLocated(
                    By.cssSelector("#members-content"), firstName + " " + lastName));
            WebElement editBtn = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//tr[td[contains(., '" + firstName + " " + lastName + "')]]//button[contains(text(), 'Edytuj')]")));
            String hxGet = editBtn.getAttribute("hx-get");
            String[] parts = hxGet.split("/");
            Long id = Long.parseLong(parts[2]);

            ((JavascriptExecutor) driver).executeAsyncScript(
                    "var done = arguments[0];" +
                    "fetch('/api/members/" + id + "', {method: 'DELETE', credentials: 'same-origin'})" +
                    "  .then(function(r) { done(r.status); })" +
                    "  .catch(function(e) { done(0); });");
        } catch (Exception ignored) {
            // cleanup best-effort
        }
    }
}
