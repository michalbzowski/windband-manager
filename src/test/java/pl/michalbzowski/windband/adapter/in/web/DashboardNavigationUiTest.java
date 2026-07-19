package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that navigating from the dashboard to sub-pages via HTMX preserves CSS.
 * Root cause being defended against: page controllers returning a FULL page
 * (with its own <head>) for HX-Request, which HTMX swaps into #content via
 * innerHTML — that drops <link> stylesheets and nests a second #content.
 *
 * We click the dashboard buttons (which fire HTMX requests) and assert the
 * returned fragment is nested inside #content (not a standalone page) and that
 * app.css is still referenced by the document.
 */
class DashboardNavigationUiTest extends UiTestBase {

    @Test
    void navigatingToRehearsalFormKeepsCssAndSingleContent() {
        loginAndNavigateTo("/");

        var button = driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]"));
        button.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsals-content")));

        assertThat(driver.findElements(By.cssSelector("#content #rehearsals-content"))).hasSize(1);

        boolean appCssLoaded = driver.findElements(By.tagName("link")).stream()
                .anyMatch(l -> l.getAttribute("href") != null && l.getAttribute("href").contains("/css/app.css"));
        assertThat(appCssLoaded).isTrue();
    }

    @Test
    void navigatingToEventsListKeepsCssAndSingleContent() {
        loginAndNavigateTo("/");

        var button = driver.findElement(By.xpath("//button[contains(., 'Dodaj wydarzenie')]"));
        button.click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#events-list-container")));

        assertThat(driver.findElements(By.cssSelector("#content #events-list-container"))).hasSize(1);

        boolean appCssLoaded = driver.findElements(By.tagName("link")).stream()
                .anyMatch(l -> l.getAttribute("href") != null && l.getAttribute("href").contains("/css/app.css"));
        assertThat(appCssLoaded).isTrue();
    }
}
