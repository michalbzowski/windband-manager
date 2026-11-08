package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI testy audytu obecności na /rehearsals/{id} (poprawki UX z przegladu widoku).
 *
 * <p>Zabezpiecza cztery kontrakty, ktore wczesniej nie byly nigdzie assertowane:
 * <ol>
 *   <li>opcje selecta statusu sa PO polsku (Brak odpowiedzi/Obecny/Usprawiedliwiony/
 *       Nieobecny) — dawniej ang. "No response/Present/...", w sprzecznosci z
 *       licznikami i modalem "Szybka obecnosc" na tej samej stronie;</li>
 *   <li>kolor selecta odzwierciedla ZAPISANY status (atraktor {@code data-current}
 *       renderowany po stronie serwera, odswiezany reloadem fragmentu po auto-zapisie),
 *       wiec "Present" i "Unexcused" roznia sie wizualnie;</li>
 *   <li>chipy filtra statusu to prawdziwe kontrolki: ikona SVG zamiast emoji,
 *       widoczna polska etykieta, role=button + tabindex + aria-pressed,
 *       przełączanie klawiatura (Enter);</li>
 *   <li>format daty w naglowku artykulu jest po polsku (nie surowe ISO),
 *       a puste "Miejsce:" nie wisi jako bledny wiersz — pokazuje podpowiedz.</li>
 * </ol>
 */
class RehearsalAttendancePolishUiTest extends UiTestBase {

    private WebElement attendanceSelect(Long memberId) {
        return driver.findElement(By.cssSelector(
                "tr[data-member-id='" + memberId + "'] select.status-select"));
    }

    private WebElement chip(String responseFilter) {
        return driver.findElement(By.cssSelector(
                "#attendance-response-filter-container .response-filter-btn"
                        + "[data-response-filter='" + responseFilter + "']"));
    }

    @Test
    void statusOptionsArePolishAndColorTracksPersistedStatus() {
        loginOnly();
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Long presentMember = createTestBand1Member("Lang" + uid, "First", null);
        Long pendingMember = createTestBand1Member("Lang" + uid, "Second", null);
        Long rehearsalId = createRehearsalViaApi("Sala " + uid, LocalDate.now().plusDays(3));
        inviteMemberToRehearsal(rehearsalId, presentMember);
        inviteMemberToRehearsal(rehearsalId, pendingMember);
        setRehearsalAttendance(rehearsalId, presentMember, "PRESENT");

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content table[role='grid']")));

        // 1) Etykiety opcji — kolejnosc i brzmienie polskie, zadnego angolizmu.
        Select sel = new Select(attendanceSelect(presentMember));
        List<String> labels = driver.findElements(By.cssSelector(
                        "#rehearsals-content select.status-select option")).stream()
                .map(o -> o.getText().trim())
                .distinct()
                .toList();
        assertThat(labels).containsExactly("Brak odpowiedzi", "Obecny", "Usprawiedliwiony", "Nieobecny");
        assertThat(driver.getPageSource())
                .doesNotContain(">Present<")
                .doesNotContain(">Unexcused<")
                .doesNotContain(">No response<");

        // 2) Zaznaczona opcja zgadza się z zapisanym statusem.
        assertThat(sel.getFirstSelectedOption().getText().trim()).isEqualTo("Obecny");

        // 3) data-current renderowany po stronie serwera + aria-label dla czytnika ekranu.
        assertThat(attendanceSelect(presentMember).getAttribute("data-current")).isEqualTo("PRESENT");
        assertThat(attendanceSelect(pendingMember).getAttribute("data-current")).isEqualTo("NO_RESPONSE");
        assertThat(attendanceSelect(presentMember).getAttribute("aria-label"))
                .startsWith("Status obecności:").contains("Lang" + uid);

        // 4) Kolor REALNIE rozroznia statusy (computed style, nie tylko atrybut).
        String presentColor = cssColor(attendanceSelect(presentMember));
        String pendingColor = cssColor(attendanceSelect(pendingMember));
        assertThat(presentColor).as("PRESENT ≠ NO_RESPONSE in computed color")
                .isNotEqualTo(pendingColor);
    }

    @Test
    void persistingNewStatusKeepsPolishLabelAndServerColor() {
        loginOnly();
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Long member = createTestBand1Member("Sync" + uid, "Test", null);
        Long rehearsalId = createRehearsalViaApi("Sala " + uid, LocalDate.now().plusDays(4));
        inviteMemberToRehearsal(rehearsalId, member);

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content table[role='grid']")));

        // Zmiana przez UI (wydarzenie change => auto-zapis + reload fragmentu).
        new Select(attendanceSelect(member)).selectByVisibleText("Nieobecny");

        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            // Fragment zostal przerysowany: select ma już stan zapisany w bazie.
            WebElement fresh = attendanceSelect(member);
            assertThat(fresh.getAttribute("data-current"))
                    .as("data-current follows the PERSISTED status after fragment reload")
                    .isEqualTo("UNEXCUSED");
        });

        // Prawda bazy po pelnym odswiezeniu.
        driver.navigate().refresh();
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content table[role='grid']")));
        WebElement afterReload = attendanceSelect(member);
        assertThat(afterReload.getAttribute("data-current")).isEqualTo("UNEXCUSED");
        assertThat(new Select(afterReload).getFirstSelectedOption().getText().trim())
                .isEqualTo("Nieobecny");
    }

    @Test
    void statusChipsArePolishIconButtonsToggleableByKeyboard() {
        loginOnly();
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Long presentMember = createTestBand1Member("Chip" + uid, "One", null);
        Long absentMember = createTestBand1Member("Chip" + uid, "Two", null);
        Long rehearsalId = createRehearsalViaApi("Sala " + uid, LocalDate.now().plusDays(5));
        inviteMemberToRehearsal(rehearsalId, presentMember);
        inviteMemberToRehearsal(rehearsalId, absentMember);
        setRehearsalAttendance(rehearsalId, presentMember, "PRESENT");
        setRehearsalAttendance(rehearsalId, absentMember, "UNEXCUSED");

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#attendance-response-filter-container")));

        // a) Ikona SVG + widoczna polska etykieta, zero emoji w markupie.
        assertThat(chip("PRESENT").getText()).contains("Obecni");
        assertThat(chip("EXCUSED").getText()).contains("Usprawiedliwieni");
        assertThat(chip("UNEXCUSED").getText()).contains("Nieobecni");
        assertThat(chip("NO_RESPONSE").getText()).contains("Brak odp.");
        for (String v : List.of("PRESENT", "EXCUSED", "UNEXCUSED", "NO_RESPONSE")) {
            WebElement c = chip(v);
            assertThat(c.findElements(By.tagName("svg")))
                    .as("chip %s carries an inline SVG icon", v).hasSize(1);
            assertThat(c.getAttribute("role")).isEqualTo("button");
            assertThat(c.getAttribute("tabindex")).isEqualTo("0");
            assertThat(c.getAttribute("aria-pressed")).isEqualTo("false");
        }
        String barText = chip("PRESENT").getText() + chip("EXCUSED").getText();
        assertThat(barText).doesNotContain("✅").doesNotContain("🕓").doesNotContain("❌");

        // b) Klawiatura: Enter wlacza filtr PRESENT — wiersz nieobecnego znika.
        chip("PRESENT").sendKeys(Keys.ENTER);
        wait.until(d -> "PRESENT".equals(d.findElement(
                By.id("active-attendance-response-filters")).getAttribute("value")));
        assertThat(chip("PRESENT").getAttribute("aria-pressed")).isEqualTo("true");
        wait.until(d -> "none".equals(attendanceSelect(absentMember)
                .findElement(By.xpath("./ancestor::tr")).getCssValue("display")));
        assertThat(attendanceSelect(presentMember)
                .findElement(By.xpath("./ancestor::tr")).getCssValue("display"))
                .isNotEqualTo("none");

        // c) Enter ponownie kasuje filtr i stan aria-pressed.
        chip("PRESENT").sendKeys(Keys.ENTER);
        wait.until(d -> "".equals(d.findElement(
                By.id("active-attendance-response-filters")).getAttribute("value")));
        assertThat(chip("PRESENT").getAttribute("aria-pressed")).isEqualTo("false");
        wait.until(d -> !"none".equals(attendanceSelect(absentMember)
                .findElement(By.xpath("./ancestor::tr")).getCssValue("display")));
    }

    @Test
    void headerDateIsPolishAndMissingLocationShowsHint() {
        loginOnly();
        String uid = UUID.randomUUID().toString().substring(0, 8);
        LocalDate date = LocalDate.now().plusDays(6);
        // createRehearsalViaApi(wskazowkaLokalizacji, data) — null => puste "Miejsce".
        Long noPlace = createRehearsalViaApi(null, date);
        driver.get(baseUrl() + "/rehearsals/" + noPlace);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content article header")));

        String dateText = driver.findElement(
                By.cssSelector("#rehearsals-content article header strong")).getText();
        assertThat(dateText).as("date formatted for pl locale, not raw ISO")
                .matches("\\p{L}+, \\d{1,2} \\p{L}+ \\d{4}")
                .doesNotContain(date.toString());
        assertThat(driver.getPageSource()).contains("Brak miejsca");

        Long withPlace = createRehearsalViaApi("Sala " + uid, date);
        driver.get(baseUrl() + "/rehearsals/" + withPlace);
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#rehearsals-content article header")));
        assertThat(driver.getPageSource())
                .contains("Miejsce: Sala " + uid)
                .doesNotContain("Brak miejsca");
    }

    private String cssColor(WebElement el) {
        return (String) ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("return getComputedStyle(arguments[0]).color;", el);
    }
}
