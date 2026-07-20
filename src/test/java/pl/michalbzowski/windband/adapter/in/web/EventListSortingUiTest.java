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
 * Verifies the event list splits into upcoming and past sections, with past
 * rows dimmed and marked "Odbyło się". Uses seed data from data.sql
 * (one future + one past event for band 1).
 */
class EventListSortingUiTest extends UiTestBase {

    @Test
    void eventList_splitsUpcomingAndPast_withBadge() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        loginAndNavigateTo("/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-list-container")));
        Thread.sleep(800);

        // Past section: the "Parada 3 Maja" (CURRENT_DATE - 10) must be dimmed + badged
        List<WebElement> pastRows = driver.findElements(By.cssSelector("#events-list-container tr.past-item"));
        assertThat(pastRows).as("past section should contain the seeded past event").isNotEmpty();

        List<WebElement> pastBadges = driver.findElements(By.cssSelector("#events-list-container .past-badge"));
        assertThat(pastBadges).as("past rows should show 'Odbyło się' badge").isNotEmpty();
        assertThat(pastBadges.get(0).getText()).contains("Odbyło się");

        // Upcoming section: the "Koncert Noworoczny" (CURRENT_DATE + 30) must NOT be a past row
        List<WebElement> allRows = driver.findElements(By.cssSelector("#events-list-container tbody tr"));
        List<WebElement> upcomingRows = new java.util.ArrayList<>(allRows);
        upcomingRows.removeAll(pastRows);
        assertThat(upcomingRows).as("upcoming section should contain the future event").isNotEmpty();
        assertThat(upcomingRows.get(0).getAttribute("class")).doesNotContain("past-item");

        System.out.println("[TEST] pastRows=" + pastRows.size() + " upcomingRows=" + upcomingRows.size()
                + " badges=" + pastBadges.size());
    }
}
