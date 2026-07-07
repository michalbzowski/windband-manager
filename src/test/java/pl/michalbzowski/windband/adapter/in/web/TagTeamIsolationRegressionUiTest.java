package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import pl.michalbzowski.windband.UiTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class TagTeamIsolationRegressionUiTest extends UiTestBase {

    @Test
    @DisplayName("Tags page shows only tags from the active team")
    void tagsPageShowsOnlyActiveTeamTags() {
        loginAndNavigateTo("/tags");

        String page = driver.findElement(By.tagName("body")).getText();

        assertThat(page).containsAnyOf("Trąbka", "Bęben", "Saksofon");
        assertThat(page).doesNotContain("Other Band Tag");
    }

    @Test
    @DisplayName("Instruments page shows only instruments from the active team")
    void instrumentsPageShowsOnlyActiveTeamInstruments() {
        loginAndNavigateTo("/instruments");

        String page = driver.findElement(By.tagName("body")).getText();

        assertThat(page).doesNotContain("Other Band Instrument");
    }
}
