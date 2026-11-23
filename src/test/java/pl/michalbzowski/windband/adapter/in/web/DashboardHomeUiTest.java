package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.TimeoutException;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for the home dashboard page ("/").
 * Verifies that the upcoming events + rehearsals list is shown first,
 * sorted chronologically, and the old hero / standalone events table are gone.
 */
class DashboardHomeUiTest extends UiTestBase {



    @Test
    void shouldShowUpcomingListAsFirstContent() {
        // cleanDatabase() wipes the whole schedule and then re-seeds the data.sql baseline
        // (2 events, 3 rehearsals with attendance) — identical on H2 and Postgres. This test
        // verifies that the baseline "Próba regularna" (+1 day, 2/2 attendance) renders: both
        // its row and a .progress-fill bar. It does NOT self-seed an extra row: a six item
        // would simply be cut by the server-side cap-5 upcoming list.
        loginAndNavigateTo("/");

        assertThat(driver.getTitle()).contains("Podsumowanie");

        // .progress-fill is only rendered when the server-side DTO has attendancePercentage
        // != null; that in turn requires a rehearsal row AND at least one attendance row.
        // We seeded both (see seedRehearsalWithAttendance above), so the dashboard should
        // show a progress bar for our seeded rehearsal within 30s on any runner.
        // Shared-CI runners execute this suite alongside scanner jobs, dast and PIT. Under
        // that load, login + navigation + SSR render can exceed the old 30s budget even though
        // the target is statically rendered by Thymeleaf (no async fetch). Bounded wait only:
        // no sleeps, assertion semantics unchanged.
        WebDriverWait progressWait = new WebDriverWait(driver, Duration.ofSeconds(60));
        try {
            progressWait.until(webDriver -> !webDriver.findElements(
                    By.cssSelector("section.dashboard-upcoming .progress-fill")).isEmpty());
        } catch (TimeoutException tmo) {
            // Diagnose-before-fix pattern: expose the mechanism on failure, not a bare timeout.
            String pageUrl = driver.getCurrentUrl();
            String pageTitle = driver.getTitle();
            var sections = driver.findElements(By.cssSelector("section.dashboard-upcoming"));
            String sectionState = sections.isEmpty()
                    ? "MISSING"
                    : "present, innerText length=" + sections.get(0).getText().length();
            throw new AssertionError(
                    "dashboard upcoming progress-fill not rendered within 60s; url=" + pageUrl
                            + ", title=" + pageTitle + ", dashboard-upcoming=" + sectionState, tmo);
        }

        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .progress-fill"));
        assertThat(progressBars).isNotEmpty();

        // Fingerprint of the baseline's first rehearsal — proves the rendered row/bar is
        // re-seeded data.sql state, not incidental leftovers from a parallel test class.
        String expectedDate = LocalDate.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String sectionText = driver.findElement(By.cssSelector("section.dashboard-upcoming")).getText();
        assertThat(sectionText)
                .as("dashboard upcoming list must contain the baseline rehearsal on %s "
                        + "(missing = reseed not applied or lost to the 5-item cap). "
                        + "Rendered section: <" + sectionText + ">", expectedDate)
                .contains(expectedDate);
        assertThat(sectionText)
                .as("baseline rehearsal must show its 2/2 attendance on %s", expectedDate)
                .contains("Obecność 2/2");

        // Check that at least one view (cards or table) has content
        var cards = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-card"));
        var tableRows = driver.findElements(By.cssSelector("section.dashboard-upcoming .upcoming-table tbody tr"));
        assertThat(cards.size() + tableRows.size()).isGreaterThan(0);

        // FAB present
        assertThat(driver.findElements(By.cssSelector(".fab"))).isNotEmpty();

        // Removed widgets must NOT be present
        assertThat(driver.findElements(By.cssSelector(".dashboard-hero"))).isEmpty();
        assertThat(driver.findElements(By.cssSelector(".dashboard-events"))).isEmpty();
    }
}
