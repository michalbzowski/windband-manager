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
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
     * <p>Members and seeded reference data (bands, instruments, dynamic groups)
     * are left intact — the seed in data.sql provides the baseline members that
     * several UI tests rely on, and re-seeding is not available after TRUNCATE.</p>
     */
    protected void cleanDatabase() {
        // H2 supports multi-table TRUNCATE; CASCADE handles FK ordering automatically.
        // Child tables first, then parents — but CASCADE makes order irrelevant.
        String allTables = "attendances, event_participations, member_instruments, "
                + "member_consent_tokens, member_consents, rehearsals, band_events, "
                + "member_attribute_values, member_attribute_defs, team_members, "
                + "members, bands, teams, users";
        try {
            jdbcTemplate.execute("TRUNCATE TABLE " + allTables + " RESTART IDENTITY CASCADE");
        } catch (Exception e) {
            // Fallback: try without RESTART IDENTITY
            try {
                jdbcTemplate.execute("TRUNCATE TABLE " + allTables + " CASCADE");
            } catch (Exception e2) {
                // Last resort: per-table with CASCADE
                for (String t : allTables.split(",")) {
                    String table = t.trim();
                    try {
                        jdbcTemplate.execute("TRUNCATE TABLE " + table + " CASCADE");
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    protected void loginAndNavigateTo(String path) {
        driver.get(baseUrl() + "/login");
        driver.findElement(By.name("username")).sendKeys("admin");
        driver.findElement(By.name("password")).sendKeys("admin");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("/login")));

        driver.get(baseUrl() + path);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("content")));
    }
}
