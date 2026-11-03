package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #224 — on the event view the date should sit on its own line under the title.
 *
 * <p>The header rendered {@code <strong>name</strong> <span>date</span>}; both are inline, so
 * the date hugged the title. The date is now a block element and the test measures real
 * geometry — a text search would pass even when the two still share one line.
 */
class EventDateOwnLineUiTest extends UiTestBase {

    @Test
    void dateStartsBelowTheTitle() {
        String name = "Koncert z datą " + UUID.randomUUID().toString().substring(0, 8);
        LocalDate date = LocalDate.now().plusDays(11);
        loginAndNavigateTo("/events");
        Long eventId = createEventViaApi(name, date);

        driver.get(baseUrl() + "/events/" + eventId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        WebElement title = wait.until(d -> d.findElement(By.cssSelector("#events-content article header strong")));
        assertThat(title.getText()).isEqualTo(name);
        WebElement dateEl = driver.findElement(By.cssSelector("#events-content article header .event-date"));
        assertThat(dateEl.getText())
                .as("the date itself must still be visible, just lower")
                .contains(String.valueOf(date));

        @SuppressWarnings("unchecked")
        List<Number> geometry = (List<Number>) js.executeScript(
                "var t = arguments[0].getBoundingClientRect(), d = arguments[1].getBoundingClientRect();"
              + "return [t.bottom, d.top, d.left, t.left];",
                title, dateEl);
        double titleBottom = geometry.get(0).doubleValue();
        double dateTop = geometry.get(1).doubleValue();
        double dateLeft = geometry.get(2).doubleValue();
        double titleLeft = geometry.get(3).doubleValue();

        assertThat(dateTop)
                .as("issue #224: the date must begin below the title, not beside it")
                .isGreaterThan(titleBottom - 1.0);
        assertThat(dateLeft)
                .as("and it should start a new line at the left edge of the header")
                .isLessThanOrEqualTo(titleLeft + 2.0);
    }
}
