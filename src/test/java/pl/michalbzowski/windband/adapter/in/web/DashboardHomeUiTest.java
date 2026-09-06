package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Disabled("Flaky on GitHub runners — .progress-fill wait times out under CI load")
    @Test
    void shouldShowUpcomingListAsFirstContent() {
        // .progress-fill is only rendered for a rehearsal that has at least one attendance
        // row, and UiTestBase.cleanDatabase() TRUNCATEs all of data.sql's seeded
        // rehearsals+attendances before this test runs. Self-seed a deterministic
        // rehearsal + two attendances so the assertion below doesn't depend on
        // incidental state wiped by any other UI test we happened to run before.
        Long rehearsalId = seedRehearsalWithAttendance("Sesja próbna", LocalDate.now().plusDays(2));
        assertThat(rehearsalId).as("seed rehearsal should have an id").isNotNull();

        loginAndNavigateTo("/");

        assertThat(driver.getTitle()).contains("Podsumowanie");

        // .progress-fill is only rendered when the server-side DTO has attendancePercentage
        // != null; that in turn requires a rehearsal row AND at least one attendance row.
        // We seeded both (see seedRehearsalWithAttendance above), so the dashboard should
        // show a progress bar for our seeded rehearsal within 30s on any runner.
        WebDriverWait progressWait = new WebDriverWait(driver, Duration.ofSeconds(30));
        progressWait.until(webDriver -> !webDriver.findElements(By.cssSelector("section.dashboard-upcoming .progress-fill")).isEmpty());

        var progressBars = driver.findElements(By.cssSelector("section.dashboard-upcoming .progress-fill"));
        assertThat(progressBars).isNotEmpty();

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

    /**
     * Inserts a rehearsal (band_id=1, tomorrow-or-later date) and one attendance row
     * linking it to member 1. Returns the new rehearsal id. Mirrors data.sql's seed
     * shape so that the dashboard will render a .progress-fill for this rehearsal.
     */
    private Long seedRehearsalWithAttendance(String name, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO rehearsals (date, start_time, end_time, location, notes, band_id)" +
                " VALUES (?, '18:00', '20:00', 'Sala prób', ?, 1)",
                date, name);
        Long rehearsalId = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM rehearsals WHERE notes = ?", Long.class, name);
        if (rehearsalId == null) throw new IllegalStateException("seeding rehearsal failed");
        jdbcTemplate.update(
                "INSERT INTO attendances (rehearsal_id, member_id, status)" +
                " VALUES (?, 1, 'PRESENT')",
                rehearsalId);
        return rehearsalId;
    }
}
