package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #268 — the members view had no search and no section filter; in a band of a few dozen
 * people the only way to find someone was scrolling.
 *
 * <p>Filters live in {@code MemberPageController} as query parameters, but the test drives the
 * real widget (typing into the box, choosing a section) and lets the page's own HTMX request do
 * the work — a hand-made URL would prove the controller, not the control. Rows coming from the
 * base fixture are ignored: only the ones carrying this run's marker are counted.
 */
class MemberFilterUiTest extends UiTestBase {

    private String marker;
    private String sectionA;
    private String sectionB;

    @Test
    void membersListCanBeFilteredByNameAndSection() {
        seedBand();
        loginAndNavigateTo("/members");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

        assertThat(seededNames())
                .as("the three seeded people are listed to begin with")
                .containsExactlyInAnyOrder(
                        "Anna Kowalska " + marker, "Jan Nowak " + marker, "Piotr Nowak " + marker);

        // 1) search by surname — typed, not opened as a link
        withRetries(() -> searchField().sendKeys("Kowalska"));
        wait.until(d -> seededNames().size() == 1);
        assertThat(seededNames()).containsExactly("Anna Kowalska " + marker);
        assertThat(driver.findElement(By.cssSelector(".member-filters-count")).getText())
                .as("the strip shows how much of the list the filter cut away")
                .contains("1 z ");

        // 2) section dropdown — cleaned with real keys, the way a person backspaces a typo
        clearSearch();
        wait.until(d -> seededNames().size() == 3);
        Select section = new Select(sectionField());
        assertThat(section.getOptions())
                .as("the dropdown offers the sections that actually occur in this band")
                .extracting(o -> o.getAttribute("value"))
                .contains(sectionA, sectionB);

        withRetries(() -> section.selectByValue(sectionB));
        wait.until(d -> seededNames().size() == 2);
        assertThat(seededNames())
                .contains("Jan Nowak " + marker, "Piotr Nowak " + marker)
                .doesNotContain("Anna Kowalska " + marker);

        // 3) both filters at once: section B plus a first name
        withRetries(() -> searchField().sendKeys("Piotr"));
        wait.until(d -> seededNames().size() == 1);
        assertThat(seededNames()).containsExactly("Piotr Nowak " + marker);

        // 4) “Wyczyść” returns the whole list and resets both controls
        driver.findElement(By.cssSelector(".member-filters-reset")).click();
        wait.until(d -> seededNames().size() == 3);
        assertThat(searchField().getAttribute("value"))
                .as("reset also empties the search term")
                .isEmpty();
        assertThat(new Select(sectionField()).getFirstSelectedOption().getAttribute("value"))
                .as("and the section picker")
                .isEmpty();
    }

    @Test
    void filteredViewIsReachableAsALink() {
        seedBand();
        loginAndNavigateTo("/members?instrument=" + URLEncoder.encode(sectionB, StandardCharsets.UTF_8));
        assertThat(seededNames())
                .as("issue #268: a filtered members view should be openable as a link")
                .containsExactlyInAnyOrder("Jan Nowak " + marker, "Piotr Nowak " + marker);
    }

    /** {@code WebElement#clear()} fires no {@code keyup}, which a live filter does not listen for. */
    private void clearSearch() {
        withRetries(() -> searchField().sendKeys(
                org.openqa.selenium.Keys.chord(org.openqa.selenium.Keys.CONTROL, "a"),
                org.openqa.selenium.Keys.DELETE));
    }

    /**
     * HTMX podmienia pasek filtrów razem z tabelą, więc element wskazany chwilę wcześniej zdąży
     * stać się nieaktualny, zanim dotrą do niego klawisze. W takim przypadku szukamy go od nowa.
     */
    private <T> T withRetries(java.util.function.Supplier<T> action) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                return action.get();
            } catch (StaleElementReferenceException gone) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(przerwane(), interrupted);
                }
            }
        }
        throw new IllegalStateException(przerwane());
    }

    private void withRetries(Runnable action) {
        withRetries(() -> {
            action.run();
            return null;
        });
    }

    private static String przerwane() {
        return "pole filtru znika przy każdej próbie — podmianka HTMX nie daje się dogonić";
    }

    private WebElement searchField() {
        return driver.findElement(By.cssSelector("#member-filters input[name='q']"));
    }

    private WebElement sectionField() {
        return driver.findElement(By.cssSelector("#member-filters select[name='instrument']"));
    }

    private List<String> visibleNames() {
        List<String> names = new java.util.ArrayList<>();
        for (WebElement row : driver.findElements(By.cssSelector("#members-table tbody tr"))) {
            try {
                if (!row.isDisplayed()) {
                    continue;
                }
                String text = row.findElement(By.tagName("td")).getText().trim();
                if (!text.isEmpty()) {
                    names.add(text);
                }
            } catch (StaleElementReferenceException swappedAway) {
                // HTMX podmienia tabelę w trakcie odczytu — wracamy pustą listą, czekanie ponowi
                return List.of();
            }
        }
        return names;
    }

    /** Tylko wiersze z tej rundy — bazowy seed ma innych członków, którzy psują liczenie. */
    private List<String> seededNames() {
        return visibleNames().stream().filter(n -> n.contains(marker)).toList();
    }

    private void seedBand() {
        marker = UUID.randomUUID().toString().substring(0, 8);
        sectionA = "Trabka-" + marker;
        sectionB = "Tuba-" + marker;
        Long trabka = createTestBand1Instrument(sectionA, 10);
        Long tuba = createTestBand1Instrument(sectionB, 11);

        Long anna = createTestBand1Member("Anna", "Kowalska " + marker, null);
        Long jan = createTestBand1Member("Jan", "Nowak " + marker, null);
        Long piotr = createTestBand1Member("Piotr", "Nowak " + marker, null);
        attachInstrument(anna, trabka, true);
        attachInstrument(jan, tuba, true);
        attachInstrument(piotr, tuba, true);
    }

    private void attachInstrument(Long memberId, Long instrumentId, boolean primary) {
        jdbcTemplate.update(
                "INSERT INTO member_instruments (member_id, instrument_id, is_primary) VALUES (?, ?, ?)",
                memberId, instrumentId, primary);
    }
}
