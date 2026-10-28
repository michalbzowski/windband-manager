package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #282 — after saving a tag, the list should pulse it green and scroll to it.
 *
 * <p>The members, events and rehearsals lists already do this through the shared
 * {@code data-focus-id} + {@code htmx:afterSwap} mechanism in {@code windband-utils.js}. Tags
 * could not take part: rows had no id at all ({@code <tr th:each="inst : ${instruments}">}),
 * {@code GET /tags/list} ignored the focus parameter, and the form threw away the id that
 * {@code POST /api/tags} answers with — so a tag added at the bottom of a long list stayed out
 * of sight and unmarked.
 *
 * <p>Fillers below push the new tag past the fold, which is the case the report is about.
 */
class TagSavedRowHighlightUiTest extends UiTestBase {

    @Test
    void savedTagIsHighlightedAndScrolledIntoView() {
        for (int i = 0; i < 24; i++) {
            createTestBand1Instrument("Wypełniacz " + i + " " + System.nanoTime());
        }
        String name = "Szukany Tag " + System.nanoTime();

        loginAndNavigateTo("/tags");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        wait.until(d -> d.findElement(By.id("add-tag-btn"))).click();
        WebElement field = wait.until(d -> d.findElement(By.cssSelector("#tag-form input[name='name']")));
        field.clear();
        field.sendKeys(name);
        js.executeScript("document.querySelector('#tag-form button[type=submit]').click();");
        WebElement row = wait.until(d -> d.findElements(By.cssSelector("#tags-content tr[id^='tag-']"))
                .stream()
                .filter(r -> r.getText().contains(name))
                .findFirst()
                .orElse(null));

        assertThat(row.getAttribute("class"))
                .as("row %s should carry the green pulse right after saving", row.getAttribute("id"))
                .contains("highlight-row");

        Object offscreen = js.executeScript(
                "var r = arguments[0].getBoundingClientRect();"
                        + "return r.top < 0 || r.bottom > window.innerHeight;", row);
        assertThat(offscreen)
                .as("the pulse must also scroll the row into view, not only colour it")
                .isEqualTo(false);
    }
}
