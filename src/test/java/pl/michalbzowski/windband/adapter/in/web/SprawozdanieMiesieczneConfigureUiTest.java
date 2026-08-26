package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Weryfikuje stronę konfiguracji raportu "sprawozdanie-miesieczne":
 * - parametry band_id / band_name są UKRYTE (type=hidden) i wypełnione z kontekstu zespołu,
 * - parametry date_from / date_to / instructor_name są WIDOCZNE dla użytkownika.
 */
class SprawozdanieMiesieczneConfigureUiTest extends UiTestBase {

    private void loginAndOpenConfigure() {
        loginViaUi();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        driver.get(baseUrl() + "/reports/configure/sprawozdanie-miesieczne");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("configure-form")));
    }

    @Test
    void bandIdAndBandNameInputsAreHiddenAndPrefilledFromTeamContext() {
        loginAndOpenConfigure();

        WebElement bandId = driver.findElement(By.cssSelector("input[name='band_id']"));
        assertThat(bandId.getAttribute("type")).as("band_id ma być ukryte").isEqualTo("hidden");
        // Admin testowy należy do zespołu 1 (data.sql)
        assertThat(bandId.getAttribute("value")).as("band_id z kontekstu zespołu").isEqualTo("1");

        WebElement bandName = driver.findElement(By.cssSelector("input[name='band_name']"));
        assertThat(bandName.getAttribute("type")).as("band_name ma być ukryte").isEqualTo("hidden");
        assertThat(bandName.getAttribute("value")).as("band_name z kontekstu zespołu").isEqualTo("Test Band");
    }

    @Test
    void promptedParametersAreVisibleInputs() {
        loginAndOpenConfigure();

        WebElement dateFrom = driver.findElement(By.id("param-date_from"));
        assertThat(dateFrom.getAttribute("type")).isEqualTo("date");
        assertThat(dateFrom.isDisplayed()).as("date_from ma być widoczne").isTrue();

        WebElement dateTo = driver.findElement(By.id("param-date_to"));
        assertThat(dateTo.getAttribute("type")).isEqualTo("date");
        assertThat(dateTo.isDisplayed()).as("date_to ma być widoczne").isTrue();

        WebElement instructor = driver.findElement(By.id("param-instructor_name"));
        assertThat(instructor.getAttribute("type")).isEqualTo("text");
        assertThat(instructor.isDisplayed()).as("instructor_name ma być widoczne").isTrue();
    }

    @Test
    void hiddenContextParametersAreNotRenderedAsVisibleInputs() {
        loginAndOpenConfigure();

        // Nie powinno być WIDOCZNEGO inputa dla band_id ani band_name
        List<WebElement> visibleBandIdLabels = driver.findElements(By.id("param-band_id"));
        assertThat(visibleBandIdLabels).as("band_id nie może mieć widocznego pola formularza").isEmpty();

        List<WebElement> visibleBandNameLabels = driver.findElements(By.id("param-band_name"));
        assertThat(visibleBandNameLabels).as("band_name nie może mieć widocznego pola formularza").isEmpty();
    }
}
