package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #225 — "Ukryj typ wydarzenia": the raw enum (Typ: CONCERT) added nothing for the user
 * and must disappear from the UI, while a new event is still stored as a CONCERT.
 *
 * <p>Both halves are asserted: the field is gone from the list, the detail page and the forms,
 * AND the hidden input keeps the API payload valid — a form that simply dropped the field would
 * send no eventType and break the create endpoint.
 */
class EventTypeHiddenUiTest extends UiTestBase {

    @Test
    void typeIsInvisibleEverywhereButStillDefaultsToConcert() {
        String name = "Wydarzenie bez typu " + UUID.randomUUID().toString().substring(0, 8);

        loginAndNavigateTo("/events");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // a list without rows would make the "no type column" check pass vacuously
        Long eventId = createEventViaApi(name, LocalDate.now().plusDays(9));
        driver.navigate().refresh();
        wait.until(d -> !d.findElements(By.cssSelector("table tbody tr")).isEmpty());

        // 1) the list has no Typ column, even though it has rows
        assertThat(driver.findElements(By.cssSelector("table tbody tr")))
                .as("the list is not empty — otherwise check 1 proves nothing")
                .isNotEmpty();
        assertThat(driver.findElements(By.cssSelector("table td[data-label='Typ']")))
                .as("issue #225: no raw enum column on /events")
                .isEmpty();
        assertThat(driver.findElements(By.cssSelector("table th")))
                .extracting(el -> el.getText())
                .as("and no orphan 'Typ' header over the remaining columns")
                .doesNotContain("Typ");

        // 2) the create form has no type picker, only the hidden default
        wait.until(d -> d.findElement(By.id("add-event-btn"))).click();
        wait.until(d -> d.findElement(By.cssSelector("#event-form input[name='name']")));
        assertThat(driver.findElements(By.cssSelector("#event-form select[name='eventType']")))
                .as("the type select must be gone from the add form")
                .isEmpty();
        WebElement hidden = driver.findElement(By.cssSelector("#event-form input[type=hidden][name='eventType']"));
        assertThat(hidden.getAttribute("value"))
                .as("a new event must still be created as a CONCERT")
                .isEqualTo("CONCERT");

        // 3) the payload the form would send still carries the type
        String serialised = (String) js.executeScript(
                "var data = new FormData(document.getElementById('event-form'));"
              + "var out = [];"
              + "data.forEach(function(v, k) { out.push(k + '=' + v); });"
              + "return out.join('&');");
        assertThat(serialised)
                .as("the hidden field must survive serialisation, or POST /api/events loses eventType")
                .contains("eventType=CONCERT");

        // 4) the detail page shows no "Typ: ..." line
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(d -> d.findElement(By.tagName("body")).getText().contains(name));

        String bodyText = driver.findElement(By.tagName("body")).getText();
        assertThat(bodyText)
                .as("issue #225: the detail view must not print the raw enum")
                .doesNotContain("Typ: CONCERT");
        assertThat(bodyText)
                .as("the event itself is still rendered")
                .contains(name);
    }
}
