package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #160 — "strzałka do tyłu i przycisk wstecz działają nie tak".
 *
 * <p>A probe on {@code origin/main} showed two concrete defects on the members list:
 * <ol>
 *   <li>the Edit button carried {@code th:hx-push-uri}, while htmx only knows
 *       {@code hx-push-url}. The attribute was ignored, the address stayed {@code /members} even
 *       though the form was swapped in, and the browser Back left the application instead of
 *       returning to the list;</li>
 *   <li>the "Szczegóły" control was a {@code <button>} with an {@code href} and no htmx verb,
 *       so clicking it did nothing at all — the URL never moved.</li>
 * </ol>
 */
class MemberHistoryNavigationUiTest extends UiTestBase {

    @Test
    void editEntersTheHistoryAndBackReturnsToTheList() {
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

        click(findButton("Edytuj"));
        wait.until(d -> d.findElement(By.id("member-form")));
        assertThat(driver.getCurrentUrl())
                .as("issue #160: opening the edit form must push its URL into the history")
                .endsWith("/edit");

        driver.navigate().back();
        wait.until(d -> d.findElement(By.tagName("table")));
        assertThat(driver.getCurrentUrl())
                .as("the Back button must return to the member list, not leave the app")
                .endsWith("/members");
        assertThat(driver.findElements(By.id("dashboardHeader")))
                .as("and the page shell with the menu has to come back with it")
                .isNotEmpty();
    }

    @Test
    void detailsButtonOpensTheMemberAndTheArrowReturnsToTheList() {
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        String listUrl = driver.getCurrentUrl();

        click(findButton("Szczegóły"));

        // 1) issue #160: the control used to be inert, so the URL never moved
        wait.until(d -> !d.getCurrentUrl().equals(listUrl));
        assertThat(driver.getCurrentUrl())
                .as("issue #160: clicking 'Szczegóły' must actually open the member")
                .contains("/members/")
                .endsWith("/detail");
        wait.until(d -> d.getPageSource().contains("Powrót do listy"));

        // 2) the in-app arrow is the other half of the report
        WebElement back = findButton("Powrót do listy");
        assertThat(back.getAttribute("href"))
                .as("the arrow has to point at the member list")
                .endsWith("/members");
        click(back);
        wait.until(d -> d.getCurrentUrl().endsWith("/members"));
        assertThat(driver.findElements(By.cssSelector("table")))
                .as("and after coming back the list is there again")
                .isNotEmpty();
    }

    /**
     * On a long list the row can sit under the sticky table header, which makes a plain
     * {@code click()} throw {@link ElementClickInterceptedException} in a full-suite run while
     * the very same element is clickable in isolation. Scrolling first is what a user does;
     * the JS fallback dispatches the same DOM click the pointer would have.
     */
    private void click(WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
        try {
            element.click();
        } catch (ElementClickInterceptedException coveredByChrome) {
            js.executeScript("arguments[0].click();", element);
        }
    }

    private WebElement findButton(String label) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        return wait.until(d -> {
            for (WebElement b : d.findElements(By.cssSelector("button, a"))) {
                if (b.isDisplayed() && b.getText().trim().contains(label)) {
                    return b;
                }
            }
            return null;
        });
    }
}
