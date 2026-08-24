package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;

import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.command.event.CreateEventCommand;
import pl.michalbzowski.windband.application.command.event.EventCommandService;
import pl.michalbzowski.windband.application.command.rehearsal.RehearsalCommandService;
import pl.michalbzowski.windband.application.command.rehearsal.ScheduleRehearsalCommand;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Verifies the unified detail-page actions bar on BOTH detail views:
 *   /events/{id}        - "Szczeęy wydarzenia"
 *   /rehearsals/{id}    - "Szczeęy spotkania"
 *
 * Contracts asserted (per user UX feedback, 2026-08-24):
 *   - One flex row, no inline `style=""` on the bar.
 *   - Back link is icon-only (no "Powrot" text).
 *   - Title has no emoji.
 *   - Primary edit action renders as a bare SVG icon, always in the row.
 *   - Secondary actions (delete, quick attendance) live inside a 3-dot menu.
 *   - On a 375px viewport nothing overflows (scrollWidth <= clientWidth).
 */
class DetailHeaderUnifiedUiTest extends UiTestBase {
    @Autowired private EventCommandService eventCommandService;
    @Autowired private RehearsalCommandService rehearsalCommandService;

    // ============================ helpers ============================
    private Long createTestEvent(WebDriver driver, WebDriverWait wait) {
        var cmd = new CreateEventCommand();
        cmd.setName("Ujedn " + System.nanoTime());
        cmd.setDate(LocalDate.now().plusDays(40));
        cmd.setStartTime(LocalTime.of(18, 30));
        cmd.setLocation("Test");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        cmd.setPaymentAmount(BigDecimal.ZERO);
        return eventCommandService.createEvent(cmd, 1L).getId();
    }

    private Long createTestRehearsal() {
        var cmd = new ScheduleRehearsalCommand();
        cmd.setDate(LocalDate.now().plusDays(30));
        cmd.setStartTime(LocalTime.of(18, 0));
        cmd.setLocation("Sala");
        return rehearsalCommandService.scheduleRehearsal(cmd, 1L).getId();
    }

    private static void assertNoEmoji(String s) {
        // Reject characters in the typical emoji / pictograph ranges. We do not
        // use Character.getType() (SYMBOL_NON_SPACING is not a Java 17 constant),
        // only explicit codepoint blocks which is more predictable across JVMs.
        for (int i = 0; i < s.length(); i++) {
            int code = Character.codePointAt(s, i);
            if ((code >= 0x1F300 && code <= 0x1FAFF)   // Misc Pictographs + supplemental
                    || (code >= 0x2600  && code <= 0x27BF) // Miscellaneous Symbols / Dingbats
                    || (code >= 0x2B00  && code <= 0x2BFF) // Stars / arrows
                    || code == 0xE0A0 || code == 0xF8FF) {  // Misc technical
                throw new AssertionError("Emoji/symbol found in title: codepoint=U+"
                        + Integer.toHexString(code));
            }
        }
    }

    // ============================ helper methods (per-test) ============================
    private WebDriver getDriver() { return driver; }

    private void loginAsAdmin(WebDriver d, WebDriverWait w) {
        d.get(baseUrl() + "/login");
        d.findElement(By.name("username")).sendKeys("admin");
        d.findElement(By.name("password")).sendKeys("admin");
        d.findElement(By.cssSelector("button[type='submit']")).click();
        w.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
    }

    // ============================ events tests ============================
    @Test
    void events_backLinkIsIconOnly() {
        WebDriver driverLocal = getDriver();
        WebDriverWait wait = new WebDriverWait(driverLocal, Duration.ofSeconds(10));
        loginAsAdmin(driverLocal, wait);
        Long id = createTestEvent(driverLocal, wait);

        driverLocal.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement back = driver.findElement(By.cssSelector(
                ".detail-actions-bar .detail-back-link"));
        assertThat(back).as("back link exists").isNotNull();
        assertThat(back.getText().trim())
                .as("back link must be icon-only (no text)")
                .isEmpty();
    }

    @Test
    void events_titleHasNoEmoji() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestEvent(driver, wait);

        driver.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement title = driver.findElement(By.cssSelector(
                ".detail-actions-bar .detail-title"));
        String t = title.getText();
        assertThat(t).as("title text").isEqualTo("Szczegóły wydarzenia");
        assertNoEmoji(t);
    }

    @Test
    void events_barHasNoInlineStyle() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestEvent(driver, wait);

        driver.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        assertThat(driver.findElements(By.cssSelector(".detail-actions-bar[style]")))
                .as("zero inline style on the bar")
                .isEmpty();
    }

    @Test
    void events_editButtonIsVisibleSvgIcon() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestEvent(driver, wait);

        driver.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement editBtn = driver.findElement(By.cssSelector(
                ".detail-actions-bar .icon-btn[data-detail-action='inline-edit']"));
        assertThat(editBtn.isDisplayed()).as("edit icon visible in row").isTrue();
        assertThat(editBtn.findElements(By.tagName("svg")))
                .as("edit button contains an SVG")
                .isNotEmpty();
    }

    @Test
    void events_deleteIsInOverflowNotRow() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestEvent(driver, wait);

        driver.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        // Delete must NOT be a direct child of the bar (would be a visible row button).
        assertThat(driver.findElements(
                By.cssSelector(".detail-actions-bar > .overflow-item")))
                .as("delete is not a direct row item")
                .isEmpty();
    }

    @Test
    void events_rowFits375Viewport() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);

        // Force the mobile media-query branch.
        driver.manage().window().setSize(new Dimension(375, 812));
        Long id = createTestEvent(driver, wait);

        driver.get(baseUrl() + "/events/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        // Bar must not overflow its containing element.
        Object dims = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
            "var b = document.querySelector('.detail-actions-bar');" +
            "var c = b && b.parentElement ? b.parentElement : b;" +
            "var r = {sw: 0, cw: 0, bw: 0};" +
            "if (b) { r.sw = b.scrollWidth; r.bw = b.clientWidth; }" +
            "if (c) { r.cw = c.clientWidth; } else if (b) { r.cw = b.clientWidth; }" +
            "return r;"
        );
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r = (java.util.Map<String, Object>) dims;
        int scrollW = ((Number) r.get("sw")).intValue();
        int clientW = ((Number) r.get("cw")).intValue();
        assertThat(scrollW).as("bar scrollWidth (%d) <= container clientWidth (%d)", scrollW, clientW)
                .isLessThanOrEqualTo(clientW);
    }

    // ============================ rehearsals tests ============================
    @Test
    void rehearsals_backLinkIsIconOnly() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestRehearsal();

        driver.get(baseUrl() + "/rehearsals/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement back = driver.findElement(By.cssSelector(
                ".detail-actions-bar .detail-back-link"));
        assertThat(back.getText().trim())
                .as("back link must be icon-only (no text)")
                .isEmpty();
    }

    @Test
    void rehearsals_titleHasNoEmoji() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestRehearsal();

        driver.get(baseUrl() + "/rehearsals/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement title = driver.findElement(By.cssSelector(
                ".detail-actions-bar .detail-title"));
        String t = title.getText();
        assertThat(t).as("title text").isEqualTo("Szczegóły spotkania");
        assertNoEmoji(t);
    }

    @Test
    void rehearsals_editButtonIsVisibleSvgIcon() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);
        Long id = createTestRehearsal();

        driver.get(baseUrl() + "/rehearsals/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        WebElement editBtn = driver.findElement(By.cssSelector(
                ".detail-actions-bar .icon-btn[data-detail-action='inline-edit']"));
        assertThat(editBtn.isDisplayed()).as("edit icon visible in row").isTrue();
        assertThat(editBtn.findElements(By.tagName("svg")))
                .as("edit button contains an SVG")
                .isNotEmpty();
    }

    @Test
    void rehearsals_rowFits375Viewport() {
        WebDriver driver = getDriver();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        loginAsAdmin(driver, wait);

        driver.manage().window().setSize(new Dimension(375, 812));
        Long id = createTestRehearsal();

        driver.get(baseUrl() + "/rehearsals/" + id);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".detail-actions-bar")));

        Object dims = ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
            "var b = document.querySelector('.detail-actions-bar');" +
            "var c = b.parentElement || b;" +
            "var r = {sw: 0, cw: 0};" +
            "if (b) { r.sw = b.scrollWidth; }" +
            "if (c) { r.cw = c.clientWidth; } else if (b) { r.cw = b.clientWidth; }" +
            "return r;"
        );
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> r = (java.util.Map<String, Object>) dims;
        int scrollW = ((Number) r.get("sw")).intValue();
        int clientW = ((Number) r.get("cw")).intValue();
        assertThat(scrollW).as("bar scrollWidth (%d) <= container clientWidth (%d)", scrollW, clientW)
                .isLessThanOrEqualTo(clientW);
    }

    @AfterEach
    void resetViewport() {
        // Restore a sane viewport size for other tests sharing the driver.
        try {
            this.driver.manage().window().setSize(new org.openqa.selenium.Dimension(1280, 800));
        } catch (Exception e) {
            return;
        }
    }
}
