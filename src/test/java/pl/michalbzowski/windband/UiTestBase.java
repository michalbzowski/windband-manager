package pl.michalbzowski.windband;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class UiTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected WebDriver driver;

    @BeforeEach
    void setUp() {
        // UI tests share a single H2 database in the JVM. Reset it before each
        // test so stale rows from a previous test cannot leak in and break
        // ordering/assertions. TRUNCATE ... CASCADE removes child rows
        // (consent tokens, attendances, participations) without FK violations.
        cleanDatabase();

        String browserPath = detectChromeBinary();
        String browserVersion = getMajorVersion(browserPath);
        System.out.println("[UiTestBase] Browser: " + browserPath + " version: " + browserVersion);

        // Try to find matching system chromedriver first
        String systemDriver = findSystemChromedriver(browserVersion);
        if (systemDriver != null) {
            System.setProperty("webdriver.chrome.driver", systemDriver);
            System.out.println("[UiTestBase] Using system chromedriver: " + systemDriver);
        } else {
            // Fall back to WebDriverManager — downloads matching version
            System.out.println("[UiTestBase] No matching system chromedriver, using WebDriverManager...");
            if (browserVersion != null) {
                WebDriverManager.chromedriver().browserVersion(browserVersion).setup();
            } else {
                WebDriverManager.chromedriver().setup();
            }
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        // Enable browser console logging at all levels
        options.setCapability("goog:loggingPrefs", java.util.Map.of("browser", "ALL"));
        if (browserPath != null) {
            options.setBinary(browserPath);
        }
        driver = new ChromeDriver(options);
        System.out.println("[UiTestBase] ChromeDriver session created successfully");
    }

    /**
     * Detects Chrome/Chromium binary across different OS/distributions:
     * Linux (Fedora/RHEL/Debian/Arch), macOS, Snap, Flatpak
     */
    private static String detectChromeBinary() {
        String[] candidates = {
                "/usr/sbin/chromium-browser",       // Fedora/RHEL
                "/usr/bin/chromium-browser",         // Debian/Ubuntu
                "/usr/bin/chromium",                 // Arch/Manjaro
                "/usr/bin/google-chrome-stable",     // Fedora/RHEL Chrome
                "/usr/bin/google-chrome",            // Debian/Ubuntu/Arch Chrome
                "/snap/bin/chromium",               // Snap
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", // macOS
                "/Applications/Chromium.app/Contents/MacOS/Chromium"           // macOS
        };
        for (String path : candidates) {
            if (new java.io.File(path).exists()) {
                return path;
            }
        }
        // Try flatpak
        try {
            Process p = new ProcessBuilder("flatpak", "info", "org.chromium.Chromium")
                    .redirectErrorStream(true).start();
            if (p.waitFor() == 0) {
                return "flatpak run org.chromium.Chromium";
            }
        } catch (Exception ignored) { /* intentionally ignored */ }
        return null;
    }

    /**
     * Extracts major version number from browser binary (e.g. "148" from "148.0.7778.96")
     */
    private static String getMajorVersion(String browserPath) {
        if (browserPath == null) return null;
        try {
            Process p = new ProcessBuilder(browserPath, "--version")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Parse "Chromium 148.0.7778.96 ..." -> "148"
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.matches("\\d+\\..*")) {
                            return part.split("\\.")[0];
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[UiTestBase] Failed to detect browser version: " + e.getMessage());
        }
        return null;
    }

    /**
     * Looks for system chromedriver that matches the browser major version.
     * Checks common locations and system PATH.
     */
    private static String findSystemChromedriver(String browserMajorVersion) {
        if (browserMajorVersion == null) return null;
        String[] candidates = {
                "/usr/lib/chromium-browser/chromedriver",
                "/usr/bin/chromedriver",
                "/usr/local/bin/chromedriver"
        };
        for (String path : candidates) {
            if (new java.io.File(path).exists()) {
                String driverVersion = getChromedriverVersion(path);
                if (driverVersion != null && driverVersion.startsWith(browserMajorVersion + ".")) {
                    return path;
                } else {
                    System.out.println("[UiTestBase] Found chromedriver at " + path +
                            " but version mismatch (driver=" + driverVersion + ", browser=" + browserMajorVersion + ")");
                }
            }
        }
        return null;
    }

    /**
     * Gets chromedriver major version (e.g. "148.0.7778.96")
     */
    private static String getChromedriverVersion(String driverPath) {
        try {
            Process p = new ProcessBuilder(driverPath, "--version")
                    .redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    // Parse "ChromeDriver 148.0.7778.96 ..." -> "148.0.7778.96"
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.matches("\\d+\\..*")) {
                            return part;
                        }
                    }
                }
            }
        } catch (Exception ignored) { /* intentionally ignored */ }
        return null;
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Reset the shared H2 test database. Called from {@link BeforeEach} so every
     * UI test starts from a clean state. Uses TRUNCATE ... CASCADE (per table —
     * H2 does not support multi-table TRUNCATE) to drop child rows (consent
     * tokens, attendances, participations, member_instruments) together with
     * their parents without tripping foreign-key constraints.
     *
     * <p>Members and seeded reference data (bands, instruments) are left intact
     * — the seed in data.sql provides the baseline members that several UI
     * tests rely on, and re-seeding is not available after TRUNCATE.</p>
     *
     * <p>The {@code member_groups} (and its junction {@code group_members})
     * tables <em>are</em> cleared and re-seeded with the 3 baseline groups
     * (Trąbki / Perkusja / Saksofony) from data.sql, because tests that create
     * manual groups leave them behind across the full suite — a sibling
     * selector that matches a partial group name then latches onto a previous
     * test's (stale) group and the wrong {@code groupId} is sent. See the
     * {@code EventInviteGroupSecondEventUiTest} fix commit for the bug this
     * caused. The 3 baseline groups are re-inserted so
     * {@code TeamIsolationRegressionUiTest} still finds them on the Test
     * Band page.</p>
     */
    protected void cleanDatabase() {
        // Clear manual-group state. Use DELETE (not TRUNCATE) because member_groups
        // has a FK to bands (which we keep) and H2's TRUNCATE ... CASCADE silently
        // no-ops on tables whose parent is not in the truncate list — the seed
        // groups then leak into the next test, breaking the unique constraint on
        // name when we try to re-seed. DELETE respects FKs without ceremony.
        try {
            jdbcTemplate.execute("DELETE FROM group_members");
            jdbcTemplate.execute("DELETE FROM member_groups");
        } catch (Exception ignored) { /* intentionally ignored — see below */ }

        // Child tables only — keep members/bands/teams/users seeded by data.sql
        // so legacy UI tests that rely on those rows keep working. CASCADE clears
        // dependent rows (consent tokens, attendances, participations) without FK violations.
        String allTables = "attendances, event_participations, member_instruments, "
                + "member_consent_tokens, member_consents, rehearsals, band_events, "
                + "member_attribute_values, member_attribute_defs, team_members";
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + allTables + " RESTART IDENTITY CASCADE");
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + allTables + " CASCADE");
            } catch (Exception e2) {
                for (String t : allTables.split(",")) {
                    String table = t.trim();
                    try {
                        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
                    } catch (Exception ignored) { /* intentionally ignored */ }
                }
            }
        }

        // Re-seed the 3 baseline groups from data.sql (lines 39-44) so the
        // team-isolation test still finds Trąbki / Perkusja on Test Band and
        // confirms Saksofony is hidden.
        jdbcTemplate.update(
                "INSERT INTO member_groups (name, description, band_id) VALUES (?, ?, ?)",
                "Trąbki", "Trębacze", 1L);
        jdbcTemplate.update(
                "INSERT INTO member_groups (name, description, band_id) VALUES (?, ?, ?)",
                "Perkusja", "Perkusyści", 1L);
        jdbcTemplate.update(
                "INSERT INTO member_groups (name, description, band_id) VALUES (?, ?, ?)",
                "Saksofony", "Saksofoniści", 2L);
    }

    protected void loginAndNavigateTo(String path) {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        driver.get(baseUrl() + path);
        // Increased timeout for pages that may load dynamic content
        wait.withTimeout(Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfElementLocated(By.id("content")));
    }

    /**
     * Creates a test instrument scoped to band 1 (the default Test Band).
     *
     * <p>After V28 the {@code instruments.band_id} column is NOT NULL and the
     * band-scoped query in {@code MemberPageController} (which feeds the
     * {@code <select name="instrumentId">} on the member form) returns only
     * rows where {@code band_id = activeTeamId}. The test admin user always
     * belongs to band 1, so any test that needs an instrument to appear in
     * that dropdown MUST create the instrument with {@code band_id = 1}.
     * Plain {@code Instrument.create(name)} produces a {@code band = null}
     * row that the dropdown will never see, breaking the test.
     *
     * @param name unique instrument name (callers should include a UUID/random
     *             suffix to avoid collisions across tests in the shared H2 DB)
     * @return the auto-generated instrument id
     */
    protected Long createTestBand1Instrument(String name) {
        return createTestBand1Instrument(name, 0);
    }

    protected Long createTestBand1Instrument(String name, int sortPriority) {
        // Plain INSERT + JdbcTemplate-generated key (works on every H2/PostgreSQL version)
        org.springframework.jdbc.support.GeneratedKeyHolder kh =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO instruments (name, description, sort_priority, band_id) VALUES (?, ?, ?, 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, "test fixture");
            ps.setInt(3, sortPriority);
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated id for test instrument: " + name);
        }
        return key.longValue();
    }

    /**
     * Helper: invite a member to an event via API (synchronous XHR).
     * Used in UI tests that need to set up event participants before testing filters.
     */
    protected void inviteMemberToEvent(Long eventId, Long memberId) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events/' + arguments[0] + '/invite', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({eventId: arguments[0], memberId: arguments[1]}));" +
                "return xhr.status;", eventId, memberId);
    }

    /**
     * Helper: set event response (CONFIRMED, DECLINED, LATER, NO_RESPONSE) via API.
     * Used in UI tests that need to configure participation responses before testing filters.
     */
    protected void setEventResponse(Long eventId, Long memberId, String response) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events/' + arguments[0] + '/response', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({eventId: arguments[0], memberId: arguments[1], response: arguments[2]}));" +
                "return xhr.status;", eventId, memberId, response);
    }

    /**
     * Helper: invite a member to a rehearsal via API (synchronous XHR).
     * Used in UI tests that need to set up rehearsal participants before testing filters.
     */
    protected void inviteMemberToRehearsal(Long rehearsalId, Long memberId) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals/' + arguments[0] + '/invite', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({rehearsalId: arguments[0], memberId: arguments[1]}));" +
                "return xhr.status;", rehearsalId, memberId);
    }

    /**
     * Helper: set rehearsal attendance status (PRESENT, EXCUSED, UNEXCUSED, NO_RESPONSE) via API.
     * Used in UI tests that need to configure attendance before testing filters.
     */
    protected void setRehearsalAttendance(Long rehearsalId, Long memberId, String status) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals/' + arguments[0] + '/attendance', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(c => c.startsWith('XSRF-TOKEN='));" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({rehearsalId: arguments[0], memberId: arguments[1], status: arguments[2]}));" +
                "return xhr.status;", rehearsalId, memberId, status);
    }
}
