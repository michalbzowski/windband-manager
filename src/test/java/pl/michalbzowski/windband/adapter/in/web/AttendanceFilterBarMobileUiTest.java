package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Responsywność paska statusów obecności na /rehearsals/{id} (zgłoszenie mobile,
 * screenshot z production 2026-11-09).
 *
 * <p>Na wąskim viewportcie (360 px) wiersz "Status:" + cztery chipy nie zawijał się do
 * drugiej linii — "Nieobecni" i "Brak odp." wypadały poza prawą krawędź ekranu i były
 * niedostępne (strona bez poziomego scrolla). Kontrakt: każdy chip musi mieścić się
 * w całości w polu widzenia, dokument nie może mieć poziomego przepełnienia, a select
 * statusu w karcie uczestnika też musi zostawać w viewportcie.</p>
 *
 * <p>Emulacja CDP (Emulation.setDeviceMetricsOverride) jak w testach listy członków;
 * override kasowany w @AfterEach — jego wyciek łamałby kolejne klasy w tym samym
 * driverze PER_CLASS.</p>
 */
class AttendanceFilterBarMobileUiTest extends UiTestBase {

    private static final int VIEWPORT_WIDTH = 360;
    private static final int VIEWPORT_HEIGHT = 640;

    @AfterEach
    void clearMobileOverride() {
        ((org.openqa.selenium.chromium.HasCdp) driver)
                .executeCdpCommand("Emulation.clearDeviceMetricsOverride", Map.of());
    }

    @Test
    void statusChipBarFitsNarrowMobileViewport() {
        loginOnly();
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Long present1 = createTestBand1Member("Resp" + uid, "Alpha", null);
        Long present2 = createTestBand1Member("Resp" + uid, "Bravo", null);
        Long excused = createTestBand1Member("Resp" + uid, "Charlie", null);
        Long unexcused = createTestBand1Member("Resp" + uid, "Delta", null);
        Long noResponse = createTestBand1Member("Resp" + uid, "Echo", null);

        Long rehearsalId = createRehearsalViaApi("Sala " + uid, LocalDate.now().plusDays(6));
        for (Long id : new Long[]{present1, present2, excused, unexcused, noResponse}) {
            inviteMemberToRehearsal(rehearsalId, id);
        }
        setRehearsalAttendance(rehearsalId, present1, "PRESENT");
        setRehearsalAttendance(rehearsalId, present2, "PRESENT");
        setRehearsalAttendance(rehearsalId, excused, "EXCUSED");
        setRehearsalAttendance(rehearsalId, unexcused, "UNEXCUSED");

        driver.get(baseUrl() + "/rehearsals/" + rehearsalId);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("#attendance-response-filter-container .response-filter-btn")));

        ((org.openqa.selenium.chromium.HasCdp) driver).executeCdpCommand(
                "Emulation.setDeviceMetricsOverride", Map.of(
                        "width", VIEWPORT_WIDTH,
                        "height", VIEWPORT_HEIGHT,
                        "deviceScaleFactor", 2,
                        "mobile", true));
        pumpFrames(6);

        JavascriptExecutor js = (JavascriptExecutor) driver;
        long innerWidth = (Long) js.executeScript("return window.innerWidth;");
        assertThat(innerWidth).as("emulacja mobilna aktywna").isEqualTo(VIEWPORT_WIDTH);

        // 1) Dokument bez poziomego przepełnienia — nic nie ucieka poza prawą krawędź.
        long docScrollWidth = (Long) js.executeScript(
                "return document.documentElement.scrollWidth;");
        assertThat(docScrollWidth)
                .as("strona nie może mieć poziomego overflowu (viewport %d, dokument %d; stan: %s)",
                        innerWidth, docScrollWidth, layoutSnapshot(js))
                .isLessThanOrEqualTo(innerWidth);

        // 2) Każdy z czterech chipów w całości w widocznym obszarze.
        for (String filter : new String[]{"PRESENT", "EXCUSED", "UNEXCUSED", "NO_RESPONSE"}) {
            WebElement chip = driver.findElement(By.cssSelector(
                    "#attendance-response-filter-container .response-filter-btn"
                            + "[data-response-filter='" + filter + "']"));
            Point corner = chip.getLocation();
            Dimension size = chip.getSize();
            assertThat(corner.getX() + size.getWidth())
                    .as("chip %s musi kończyć się przed prawą krawędzią (lewa=%d, szer=%d; stan: %s)",
                            filter, corner.getX(), size.getWidth(), layoutSnapshot(js))
                    .isLessThanOrEqualTo(VIEWPORT_WIDTH);
        }

        // 3) Select statusu w karcie uczestnika mieści się w viewportcie.
        WebElement select = driver.findElement(By.cssSelector(
                "tr[data-member-id='" + present1 + "'] select.status-select"));
        assertThat(select.getLocation().getX() + select.getSize().getWidth())
                .as("select statusu mieści się w viewportcie mobilnym")
                .isLessThanOrEqualTo(VIEWPORT_WIDTH);
    }

    /** Zwięzły snapshot geometrii paska — naprowadza diagnozę prosto w raport testu. */
    private String layoutSnapshot(JavascriptExecutor js) {
        Object value = js.executeScript(
                "var c = document.getElementById('attendance-response-filter-container');"
                        + "var cs = getComputedStyle(c);"
                        + "var out = 'container{display:' + cs.display + ',ws:' + cs.whiteSpace"
                        + " + ',width:' + Math.round(c.getBoundingClientRect().width)"
                        + " + ',scrollW:' + c.scrollWidth + '}';"
                        + "for (var i = 0; i < c.children.length; i++) {"
                        + "  var e = c.children[i];"
                        + "  var r = e.getBoundingClientRect();"
                        + "  out += ' | ' + (e.getAttribute('data-response-filter') || e.className)"
                        + "    + '{x:' + Math.round(r.left) + ',w:' + Math.round(r.width) + '}';"
                        + "}"
                        + "return out;");
        return String.valueOf(value);
    }

    private void pumpFrames(int count) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        for (int i = 0; i < count; i++) {
            js.executeScript("return new Promise(r => requestAnimationFrame(() => r(true)));");
        }
    }
}
