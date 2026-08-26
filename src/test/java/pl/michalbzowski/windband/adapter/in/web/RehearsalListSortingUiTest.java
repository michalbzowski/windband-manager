package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies the rehearsal list splits into upcoming (ascending by date) and
 * past (descending by date, dimmed with "Odbyło się" badge) sections.
 */
class RehearsalListSortingUiTest extends UiTestBase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Test
    void rehearsalList_splitsUpcomingAndPast_sortedCorrectly() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDate in30 = LocalDate.now().plusDays(30);

        // Create a past rehearsal (yesterday)
        createRehearsal(yesterday, "Przeszla A");
        // Create two future rehearsals (tomorrow, in 30 days) -> upcoming section should be ascending
        createRehearsal(tomorrow, "Nadchodzaca B");
        createRehearsal(in30, "Nadchodzaca C");

        // Open rehearsal list (full page load) and wait for at least one row to settle
        loginAndNavigateTo("/rehearsals");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("rehearsals-content")));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content tbody tr")));

        // Past section must exist with the "Odbyło się" badge + dimmed rows
        List<WebElement> pastRows = driver.findElements(By.cssSelector("#rehearsals-content tr.past-item"));
        assertThat(pastRows).as("past section should contain the yesterday rehearsal").isNotEmpty();

        List<WebElement> pastBadges = driver.findElements(By.cssSelector("#rehearsals-content .past-badge"));
        assertThat(pastBadges).as("past rows should show 'Odbyło się' badge").isNotEmpty();

        // Upcoming rows = all rows minus past rows
        List<WebElement> allRows = driver.findElements(By.cssSelector("#rehearsals-content tbody tr"));
        List<WebElement> upcomingRows = new ArrayList<>(allRows);
        upcomingRows.removeAll(pastRows);
        assertThat(upcomingRows).as("upcoming section should have at least the 2 future rehearsals we created").hasSizeGreaterThanOrEqualTo(2);

        // Order checks — verify our two future dates are present and ascending
        List<LocalDate> upcomingDates = extractDates(upcomingRows);
        assertThat(upcomingDates).as("upcoming must contain nearest-first (ascending) future dates")
                .contains(tomorrow, in30);
        // The list must be sorted ascending overall (every element >= previous)
        assertThat(isSortedAscending(upcomingDates))
                .as("upcoming list must be sorted nearest-first (ascending)").isTrue();

        List<LocalDate> pastDates = extractDates(pastRows);
        assertThat(pastDates).as("past must contain the yesterday rehearsal").contains(yesterday);
        assertThat(isSortedDescending(pastDates))
                .as("past list must be sorted most-recent-first (descending)").isTrue();

        System.out.println("[TEST] upcoming=" + upcomingDates + " past=" + pastDates);
    }

    private boolean isSortedAscending(List<LocalDate> list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).isBefore(list.get(i - 1))) return false;
        }
        return true;
    }

    private boolean isSortedDescending(List<LocalDate> list) {
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).isAfter(list.get(i - 1))) return false;
        }
        return true;
    }

    private List<LocalDate> extractDates(List<WebElement> rows) {
        List<LocalDate> dates = new ArrayList<>();
        for (WebElement row : rows) {
            // First cell holds the date text (may include the badge in past rows)
            String text = row.findElement(By.cssSelector("td")).getText().replace("Odbyło się", "").trim();
            dates.add(LocalDate.parse(text, DATE_FMT));
        }
        return dates;
    }

    private void createRehearsal(LocalDate date, String location) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAndNavigateTo("/rehearsals");
        driver.findElement(By.xpath("//button[contains(., 'Zaplanuj spotkanie')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#rehearsal-form")));
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='date']\").value = '" + date + "';");
        driver.findElement(By.cssSelector("input[name='startTime']")).sendKeys("18:00");
        driver.findElement(By.cssSelector("input[name='endTime']")).sendKeys("20:00");
        driver.findElement(By.cssSelector("input[name='location']")).sendKeys(location);
        driver.findElement(By.cssSelector("#rehearsal-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.urlContains("/rehearsals"));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/new")));
        // Awaitility: czekamy aż rehearsal pojawi się w DB (post-submit write musi być sflushowany
        // zanim następna próba go użyje albo zanim lista go odczyta)
        String dateStr = date.toString();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM rehearsals WHERE date = ? AND location = ?",
                    Integer.class, java.sql.Date.valueOf(dateStr), location);
            assertThat(count).isEqualTo(1);
        });
    }
}
