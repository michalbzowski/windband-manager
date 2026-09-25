package pl.michalbzowski.windband;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
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

    protected WebDriver driver;

    @BeforeEach
    void setUp() {
        // UI tests share a single H2 database in the JVM. Reset it before each
        // test so stale rows from a previous test cannot leak in and break
        // ordering/assertions. TRUNCATE ... CASCADE removes child rows
        // (consent tokens, attendances, participations) without FK violations.
        // clean up stale DB state before every test method (see cleanDatabase below).
        // We cannot clear browser cookies here: the shared ChromeDriver session is
        // created in @BeforeAll and its JSESSIONID belongs to the Spring Security
        // session that will be re-issued by doLogin() if needed.  The DB-level reset
        // (cleanDatabase) is what guarantees test isolation — Spring sessions are
        // in-memory and survive TRUNCATE, so a stale login cookie is harmless:
        // Reset state flags before each test
        sessionEstablished = false; // Force re-authentication after cleanDatabase() TRUNCATE
        cleanDatabase();
        reseedDeletedMembersIfNeeded(); // Ensure members exist for FK references in consent tables
        cleanupOrphanMemberConsents(); // Drop orphaned consent/token rows left by prior tests
    }

    /**
     * Launches ONE browser session per test class (instead of one per test method),
     * sharing the chromedriver+Chromium process across every {@code @Test} in
     * the subclass. Test isolation is preserved by the {@code @BeforeEach}
     * database reset — the shared driver only carries the login/CSRF state,
     * which {@code doLogin()} re-establishes on each test as needed.
     */
    @BeforeAll
    void launchBrowser() {
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

    @AfterAll
    void tearDownClass() {
        if (driver != null) {
            driver.quit();
            driver = null;
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
        new WebDriverWait(driver, Duration.ofSeconds(5)).pollingEvery(Duration.ofMillis(100))
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
        // IMPORTANT: Do NOT truncate member_consent_tokens / member_consents —
        // these are needed by Spring Security to authorize admin after each login.
        // TRUNCATEing them mid-test causes 302 → /login loops (unterminated session).
        String allTables = "attendances, event_participations, member_instruments, " +
                "rehearsals, band_events, member_attribute_values, member_attribute_defs, team_members, " +
                "compositions, composition_instruments, score_files";
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

    private void reseedDeletedMembersIfNeeded() {
        String memberInsert = "INSERT INTO members (first_name, last_name, date_of_birth, email, phone, active, joined_date, email_consent_given, band_id) " +
                             "VALUES (?, ?, ?, ?, ?, ?, CURRENT_DATE, false, 1) RETURNING id";
        try {
            Long janExisting = jdbcTemplate.queryForObject(
                    "SELECT id FROM members WHERE first_name = 'Jan' AND last_name = 'Kowalski'",
                    Long.class);
            if (janExisting == null) {
                Object[] params = {"Jan", "Kowalski", "1990-05-15", "jan@test.com", "123456789", true};
                long janId = jdbcTemplate.queryForObject(memberInsert, Long.class, params);
                System.out.println("[seed] Re-seeded Jan Kowalski (id=" + janId + ")");
            }

            Long annaExisting = jdbcTemplate.queryForObject(
                    "SELECT id FROM members WHERE first_name = 'Anna' AND last_name = 'Nowak'",
                    Long.class);
            if (annaExisting == null) {
                Object[] params = {"Anna", "Nowak", "1985-03-20", "anna@test.com", "987654321", true};
                long annaId = jdbcTemplate.queryForObject(memberInsert, Long.class, params);
                System.out.println("[seed] Re-seeded Anna Nowak (id=" + annaId + ")");
            }

            Integer countBand1 = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM members WHERE band_id = 1", Integer.class);
            if (countBand1 == null || countBand1.intValue() < 2) {
                System.err.println("[seed] WARNING: Expected at least Jan+Anna in band 1, found " + countBand1);
            }
        } catch (Exception e) {
            // Non-fatal: log warning and continue — if seed fails, other tests might still pass via data.sql fallback
            System.err.println("[seed] Could not reseed members (ignoring): " + e.getMessage());
        }
    }


    /**
     * CRITICAL FIX (PostgreSQL CI): a previous test class may delete Member rows while the
     * async welcome/consent flow still holds references to them. Such orphaned rows in the
     * consent tables then violate FKs when Spring Security / listeners re-touch members, so we
     * remove consent + token rows whose parent member no longer exists. Runs before every test.
     */
    private void cleanupOrphanMemberConsents() {
        try {
            jdbcTemplate.update("DELETE FROM member_consents mc WHERE NOT EXISTS "
                    + "(SELECT 1 FROM members m WHERE m.id = mc.member_id)");
            jdbcTemplate.update("DELETE FROM member_consent_tokens mct WHERE NOT EXISTS "
                    + "(SELECT 1 FROM members m WHERE m.id = mct.member_id)");
        } catch (Exception e) {
            System.err.println("[cleanup] Could not remove orphan consent rows (ignoring): " + e.getMessage());
        }
    }

    protected void loginAndNavigateTo(String path) {
        // Reuse the login flow for consistency, then navigate.
        doLogin();
        driver.get(baseUrl() + path);
        new WebDriverWait(driver, Duration.ofSeconds(30)).pollingEvery(Duration.ofMillis(100))
            .until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.id("content")),
                    ExpectedConditions.presenceOfElementLocated(By.id("compositions-content")),
                    ExpectedConditions.presenceOfElementLocated(By.id("composition-detail"))));
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

    /**
     * Establishes the authenticated session WITHOUT navigating to any
     * destination page. After the login form submits, the browser already sits
     * on a same-origin page (Spring's default success URL), which is all the
     * synchronous XHR seed helpers ({@code createEventViaApi},
     * {@code inviteMemberToEvent}, {@code setRehearsalAttendance}, …) need for
     * their session + CSRF context. Use instead of
     * {@code loginAndNavigateTo("/some/list")} whenever the list page is NOT
     * the subject of the test — it skips one full server-rendered page load
     * (~0.5 s per test method). Login itself stays per-test on purpose:
     * {@code cleanDatabase()} TRUNCATEs shared tables, so the shared browser
     * session must be re-established for every test (see the false-green trap
     * documented in the spring-boot-selenium-tests skill).
     */
    protected void loginOnly() {
        doLogin();
    }

    private boolean sessionEstablished = false;

    private void doLogin() {
        if (sessionEstablished) {
            return; // session persists across the test class's browser instance
        }
        driver.get(baseUrl() + "/login");
        new WebDriverWait(driver, Duration.ofSeconds(10)).pollingEvery(Duration.ofMillis(100))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("form[action='/login'] input[name='username']")));

        FluentWait<WebDriver> w = new WebDriverWait(driver, Duration.ofSeconds(10)).pollingEvery(Duration.ofMillis(100));
        WebElement usernameField = w.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("form[action='/login'] input[name='username']")));
        usernameField.clear();
        usernameField.sendKeys("admin");

        WebElement passwordField = w.until(ExpectedConditions.visibilityOfElementLocated(By.name("password")));
        passwordField.clear();
        passwordField.sendKeys("admin");

        driver.findElement(By.cssSelector("button[type='submit']")).click();
        w.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));
        sessionEstablished = true;
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

    /**
     * Inserts a band-1 member directly into the shared H2 test database.
     * Used by UI tests whose purpose is NOT to verify the member form — they
     * need a deterministic, unique member row for tagging / filtering
     * scenarios and driving the form per-member adds ~250 ms of login/POST
     * overhead per seed (loginAndNavigateTo + submit wait). This helper is
     * the fast path; UI tests that genuinely test the member form keep their
     * own inline flow.
     *
     * <p>Required NOT-NULL columns are filled with sensible defaults
     * ({@code active=true}, {@code joinedDate=today}); the caller controls the
     * name and (optionally) date of birth so filter / search tests can rely on
     * exact strings. Callers should include a UUID suffix in the name, as with
     * every other seed helper in this class.
     *
     * @param firstName   required, stored verbatim
     * @param lastName    required, stored verbatim
     * @param dateOfBirth nullable; use null for "no DOB" rows
     * @return auto-generated member id
     */
    protected Long createTestBand1Member(String firstName, String lastName, java.time.LocalDate dateOfBirth) {
        org.springframework.jdbc.support.GeneratedKeyHolder kh =
                new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO members (first_name, last_name, date_of_birth, active, joined_date, email_consent_given, band_id) " +
                    "VALUES (?, ?, ?, TRUE, CURRENT_DATE, FALSE, 1)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            if (dateOfBirth != null) {
                ps.setDate(3, java.sql.Date.valueOf(dateOfBirth));
            } else {
                ps.setNull(3, java.sql.Types.DATE);
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to obtain generated id for test member: " + firstName);
        }
        return key.longValue();
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

    /**
     * Helper: create a band event via API (synchronous XHR) and return its generated id.
     * Used by UI tests that need a deterministic event row without driving the full form
     * flow — same pattern as {@link #createRehearsalViaApi}. The browser must already be
     * on a same-origin page; callers navigate to the target page right after.
     */
    protected Long createEventViaApi(String name, LocalDate date) {
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
        Object idObj = js.executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "var csrf = document.cookie.split('; ').find(function (c) { return c.startsWith('XSRF-TOKEN='); });" +
                "if (csrf) xhr.setRequestHeader('X-XSRF-TOKEN', csrf.split('=')[1]);" +
                "xhr.send(JSON.stringify({name: arguments[0], date: arguments[1], startTime: '18:00', location: 'Sala koncertowa', eventType: 'CONCERT'}));" +
                "var parsed = JSON.parse(xhr.responseText); return parsed && parsed.id !== undefined ? String(parsed.id) : null;",
                name, date.toString());
        if (idObj == null) return null;
        String id = (idObj instanceof Number n) ? String.valueOf(n) : String.valueOf(idObj);
        return id.isEmpty() ? null : Long.valueOf(id);
    }

    /**
     * Fills the {@code email}/{@code phone} columns of a member previously created by
     * {@link #createTestBand1Member}. Needed by UI tests whose form-edit flow pre-sets
     * those fields (the SQL insert leaves them null).
     */
    protected void addEmailPhoneToMember(Long memberId, String email, String phone) {
        jdbcTemplate.update(
                "UPDATE members SET email = ?, phone = ? WHERE id = ?",
                email, phone, memberId);
    }
}
