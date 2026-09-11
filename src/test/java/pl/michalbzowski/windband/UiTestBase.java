package pl.michalbzowski.windband;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.JavascriptExecutor;
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
import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class UiTestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /** Lazily-initialised, once-per-JVM (shared by every UI test class) ChromeDriver. */
    private static WebDriver sharedDriver;
    private static final Object DRIVER_LOCK = new Object();

    protected WebDriver driver;

    @BeforeEach
    void setUp() {
        // Attach the process-wide shared ChromeDriver (launched once per JVM) so
        // that 66 UI test classes do NOT each start their own browser. This is
        // what was blowing up RAM when every class called launchBrowser() in
        // its own @BeforeAll: 66 concurrent headless-Chrome sessions piled onto
        // one H2/Postgres. A single shared driver keeps one session while still
        // giving every class a fresh DOM — isolation is provided by the
        // cleanDatabase() reset below plus per-test navigation in each test.
        driver = ensureSharedDriver();

        // Because we now share ONE Chrome across all UI test classes, cookies
        // (login session, XSRF-TOKEN) and localStorage (data-theme, etc.) persist
        // between tests — that used to be fine because every class got its own
        // browser with a blank profile. Reset browser state before every test so
        // each one sees a clean profile exactly as if it were the first test in
        // a fresh Chrome. cheap: driver.manage().deleteAllCookies() + clear JS
        // storage in the current origin (localhost:port) — no network, no reload.
        wipeBrowserState();

        // UI tests share a single H2 database in the JVM. Reset it before each
        // test so stale rows from a previous test cannot leak in and break
        // ordering/assertions. TRUNCATE ... CASCADE removes child rows
        // (consent tokens, attendances, participations) without FK violations.
        cleanDatabase();
    }

    /** Nukes cookies + localStorage/sessionStorage on the current origin and drains the browser console log. Called before every test so the shared driver behaves as a fresh profile each time AND no stale SEVERE/ERROR console entries from earlier tests pollute "console must be clean" assertions. */
    private void wipeBrowserState() {
        try {
            // Ensure we're on the application origin first (cookies are scoped to origin)
            if (!driver.getCurrentUrl().startsWith(baseUrl())) {
                driver.get(baseUrl());
            }
            driver.manage().deleteAllCookies();
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("try{localStorage.clear()}catch(e){}try{sessionStorage.clear()}catch(e){}");

            // Drain any console entries accumulated by EARLIER tests — otherwise
            // Selenium's goog:loggingPrefs log buffer carries over a 500/409 from
            // a prior test into this test's "console must be clean" assertion. Read-then-discard clears it.
            try {
                driver.manage().logs().get(org.openqa.selenium.logging.LogType.BROWSER);
            } catch (Exception ignored) { /* older Selenium: no log API — still fine */ }

            // Also clear the in-page JS console buffer (if the test's own helper uses window.console)
            js.executeScript("try{window.__consoleErrors=[].concat(window.__consoleErrors||[])}catch(e){}");
        } catch (Exception e) {
            System.err.println("[UiTestBase] wipeBrowserState: " + e.getMessage());
        }
    }

    /**
     * Returns the singleton ChromeDriver, launching it exactly once per JVM on
     * first use (thread-safe). Every UI test class in this fork shares this one
     * driver, so we only pay the ~300MB + startup cost ONCE for the whole suite
     * instead of once per class.
     *
     * <p>Test isolation is still preserved even though the driver is shared:
     * each test navigates via {@code driver.get(baseUrl() + path)}, which fully
     * resets the DOM and page state, and {@code cleanDatabase()} (called in
     * {@code @BeforeEach}) wipes the shared H2 rows. The browser holds no
     * application data we care about between tests — only cookies (the login
     * session), which {@code doLogin()} re-establishes as needed.</p>
     */
    private static WebDriver ensureSharedDriver() {
        if (sharedDriver != null) {
            return sharedDriver;
        }
        synchronized (DRIVER_LOCK) {
            if (sharedDriver == null) {
                sharedDriver = createChromeDriver();
            }
        }
        return sharedDriver;
    }

    /** Builds one headless ChromeDriver session. Extracted so ensureSharedDriver stays small. */
    private static WebDriver createChromeDriver() {
        String browserPath = detectChromeBinary();
        String browserVersion = getMajorVersion(browserPath);
        System.out.println("[UiTestBase] Browser: " + browserPath + " version: " + browserVersion);

        // Try to find matching system chromedriver first — otherwise WebDriverManager.
        String systemDriver = findSystemChromedriver(browserVersion);
        if (systemDriver != null) {
            System.setProperty("webdriver.chrome.driver", systemDriver);
            System.out.println("[UiTestBase] Using system chromedriver: " + systemDriver);
        } else {
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
        WebDriver d = new ChromeDriver(options);
        System.out.println("[UiTestBase] Shared ChromeDriver session created once for the whole JVM");
        return d;
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

    // NOTE: We intentionally do NOT quit the driver in a per-class @AfterAll,
    // because the ChromeDriver is now a JVM-wide singleton shared by all 66 UI
    // test classes. Quitting it after one class would break every subsequent
    // class ("session deleted") and/or cause us to re-launch — defeating the
    // whole point of shared-driver + killing RAM when forks re-spawn Chrome.
    // The browser process is reclaimed when the test JVM exits (or, when a fork
    // finishes) — surefire kills the OS-level chromedriver/Chromium children as
    // part of normal fork teardown on a clean JVM shutdown. If you need an
    // explicit quit (e.g. running a single class from an IDE), call:
    //     UiTestBase.quitSharedDriverForTesting();

    public static void quitSharedDriverForTesting() {
        synchronized (DRIVER_LOCK) {
            if (sharedDriver != null) {
                try { sharedDriver.quit(); } catch (Exception ignored) { /* already dead */ }
                sharedDriver = null;
            }
        }
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Opens the {@code ⋮} overflow menu on the unified detail-actions-bar so
     * that a hidden inner action (e.g. {@code #delete-event-btn},
     * {@code #quick-attendance-btn}) becomes interactable, then clicks it.
     *
     * <p>The bar (fragments/detail-page-actions-bar.html) keeps only "Edytuj"
     * in the visible row on all viewports — every secondary action is tucked
     * under the 3-dot menu per product design (see DetailHeaderUnifiedUiTest).
     * The menu toggle is a native DOM click, so this helper just simulates that
     * click and then falls through to the inner button. Used by every UI test
     * that previously clicked a now-hidden inner action directly:
     * EventDeleteConfirmModalUiTest, QuickAttendanceModalUiTest,
     * RehearsalInviteAfterQuickAttendanceUiTest,
     * and EventRehearsalDetailActionsBarUiTest.</p>
     */
    protected void clickOverflowInnerButton(String innerButtonId) {
        driver.findElement(By.cssSelector(
                ".detail-actions-bar .icon-btn[data-detail-action='toggle-more']")).click();
        new WebDriverWait(driver, Duration.ofSeconds(5))
             .until(ExpectedConditions.visibilityOfElementLocated(By.id(innerButtonId)));
        driver.findElement(By.id(innerButtonId)).click();
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
        // Reuse the login flow for consistency, then navigate.
        doLogin();
        driver.get(baseUrl() + path);
        new WebDriverWait(driver, Duration.ofSeconds(30))
            .until(ExpectedConditions.presenceOfElementLocated(By.id("content")));
    }

    /**
     * Performs the UI login. Waits for the username/password fields to be
     * visible before typing so we don't race Chrome-151 runner timing (which
     * used to cause NoSuchElementException in CI). Idempotent — if the session
     * is already established and the browser is NOT on /login, this short-circuits.
     */
    protected void loginViaUi() {
        doLogin();
        // Some tests call loginViaUi() expecting "session established, page ready"
        // without a specific destination. Navigate to root so we're safely somewhere.
        driver.get(baseUrl() + "/");
    }

    private void doLogin() {
        String cur = driver.getCurrentUrl();
        boolean onLogin = (cur != null && cur.contains("/login"));

        // If we're NOT on /login, a prior login should still be in effect — but to
        // be safe for the repeated-login pattern tests like RehearsalListSortingUiTest
        // use, we always force a fresh POST /login cycle below. (Spring Security's
        // session persists across pages, so re-logging-in is just a fast no-op.)

        driver.get(baseUrl() + "/login");

        WebDriverWait w = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement usernameField = w.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")));
        usernameField.clear();
        usernameField.sendKeys("admin");

        WebElement passwordField = w.until(ExpectedConditions.visibilityOfElementLocated(By.name("password")));
        passwordField.clear();
        passwordField.sendKeys("admin");

        driver.findElement(By.cssSelector("button[type='submit']")).click();

        w.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
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
     * Helper: create a future-dated rehearsal via API (synchronous XHR) and
     * return its generated id. Used by UI tests that need a deterministic,
     * unique rehearsal instance without driving the whole form flow.
     */
    protected Long createRehearsalViaApi(String nameHint, LocalDate date) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        Object idObj = js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/rehearsals', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(function (c) { return c.startsWith('XSRF-TOKEN='); });" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({date: arguments[0], startTime: '18:00', endTime: '20:00', location: arguments[1]}));" +
                "var parsed = JSON.parse(xhr.responseText); return parsed && parsed.id !== undefined ? String(parsed.id) : null;",
                date.toString(), nameHint == null ? "" : nameHint);
        if (idObj == null) return null;
        // Selenium returns a Long (for JSON number ids) or a String — normalise.
        String id = (idObj instanceof Number n) ? String.valueOf(n) : String.valueOf(idObj);
        return id.isEmpty() ? null : Long.valueOf(id);
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
