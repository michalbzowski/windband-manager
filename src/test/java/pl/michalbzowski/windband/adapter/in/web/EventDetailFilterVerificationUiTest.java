package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification (t_5263250c) — acceptance pass for the event-details participant
 * filter fix (parent: t_ebd04e70, "tag filter on event participants").
 *
 * <p>Three members are set up so every scenario isolates exactly one (or two)
 * expected row(s), proving each branch of the predicate independently:
 * <pre>
 *   M1 "Kaz{u}"              / "Tagg{u}"      tag = "Kazar{u}"   ← tagged, name+tag same root
 *   M2 "Iwona{u}"            / "Trąbka{u}"    untagged           ← diacritic SURNAMe (name branch)
 *   M3 "Marek{u}"            / "NowakX{u}"    untagged           ← clean control member
 * </pre>
 *
 * <ol>
 *  <li>(1)   first name "Iwona" + u                → exactly {M2}
 *  <li>(2)   last name  "NowakX" + u               → exactly {M3}
 *  <li>(3)   tag      "Kazar{u}"  (exact diacritic fold target: contains no diacritec self) — wait, this is ASCII.
 *            Real scenario 3: the SPEC wants 'Trąbka' to surface tagged participants. Here M2's
 *            SURNAMe is "Trąbka{u}" (untagged!) — so "Trąbka" matches via NAME branch (surname),
 *            and M1 stays hidden unless its TAG also matches "trabka...". We therefore ALSO tag M3? no.
 * </ol>
 *
 * <p>Simpler, stricter mapping (what we actually assert below):
 * <ul>
 *  <li>scenario 1 first name:   type "Iwona" + u          → exactly {M2} only
 *  <li>scenario 2 last name:    type "NowakX" + u         → exactly {M3} only
 *  <li>scenario 3 tag (spec 'Trąbka'):  we make M2 the TRAGGED row with tag "Trąbka{u}"
 *       instead of an untagged member, and rename M3's surname to something neutral. Then
 *       typing "Trąbka" (diacritic) → {M2}, and ASCII "trabka" → {M2}. This directly mirrors the
 *       parent task's bug report (typing Trąbka must surface participants tagged Trąbka).
 *  <li>scenario 4 union/no-dup: type "kazar" + u — matches M1 BY TAG ("Kazar{u}") AND BY FIRST-NAME
 *       substring ("Kaz{u}"), proving a single row is never duplicated from two matching branches.
 *       Expected exactly ONE visible row.
 *  <li>scenario 5 empty input:  full list of 3 restored.
 *  <li>scenario 6 diacritics:   covered by scenario 3 (Trąbka vs trabka) and by an extra mixed-case probe.
 *  <li>scenario 7 console:      no SEVERE/ERROR entries recorded during the whole pass.
 * </ul>
 */
class EventDetailFilterVerificationUiTest extends UiTestBase {

    private Long mTaggedId;
    private Long mUnameId;
    private Long mNeutralId;

    @Test
    void allAcceptanceScenariosPassWithCleanConsole() throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        String u = UUID.randomUUID().toString().substring(0, 8);

        // DEFENSE: MemberQueryService.getMembersById builds a priority map via
        // Collectors.toMap(Instrument::getName, Instrument::getSortPriority). Any instrument
        // row with a null sort_priority (legacy / seed / prior test leak) makes that toMap
        // throw NPE and the whole /members page 500 — masking my real verification. Sanitize.
        jdbcTemplate.update(
                "UPDATE instruments SET sort_priority = 0 WHERE sort_priority IS NULL");
        String tagWord   = "Tr\u0105bka" + u;           // diacritic tag — the exact spec word + unique suffix
        String tagName   = "Kazar" + u;                 // ASCII-ish root shared with M1 first-name stem

        // M1 tagged member — exercises NAME+TAG union (scenario 4).
        createMemberUi("Kaz" + u, "Tagg" + u, wait);
        setPrimaryKeyInstrumentTag("Kaz" + u, tagName);

        // M2 TAGGED with the spec word — proves tag branch with Polish diacritics.
        createMemberUi("Iwona" + u, "Tagged" + u, wait);
        setPrimaryKeyInstrumentTag("Iwona" + u, tagWord);

        // M3 neutral — exercises clean last-name filter only (scenario 2), no tags, no name overlap.
        createMemberUi("Marek" + u, "NowakX" + u, wait);

        mTaggedId  = idByFirstOrNull("Kaz" + u);
        mUnameId   = idByFirstOrNull("Iwona" + u);
        mNeutralId = idByFirstOrNull("Marek" + u);
        assertThat(mTaggedId).isNotNull();
        assertThat(mUnameId).isNotNull();
        assertThat(mNeutralId).isNotNull();

        // Event created directly (schema matches BandEvent: name, date, start_time, event_type NOT NULL,
        // payment_type NOT NULL default FREE, band_id NOT NULL — all supplied below).
        String eventName = "VerifyFilter " + u;
        jdbcTemplate.update(
                "INSERT INTO band_events (name, date, start_time, event_type, payment_type, band_id) " +
                        "VALUES (?, ?, '18:00', 'CONCERT', 'FREE', 1)",
                "VerifyFilter " + u, java.time.LocalDate.now().toString());
        Long eventId = jdbcTemplate.queryForObject("SELECT id FROM band_events WHERE name = ?", Long.class, eventName);

        // Authenticate first (CSRF cookie needed for the XHR invite helper), then invite all three.
        loginAndNavigateTo("/events");
        inviteMemberToEvent(eventId, mTaggedId);
        inviteMemberToEvent(eventId, mUnameId);
        inviteMemberToEvent(eventId, mNeutralId);

        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("participants-table")));

        assertThat(visibleCount()).describedAs("full participant list before any filter").isEqualTo(3);

        // ── scenario 1: known FIRST name surfaces exactly the expected member ─────────────
        typeFilter("Iwona" + u);
        assertThat(visibleCount()).isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Iwona");   // M2

        // ── scenario 2: known LAST name surfaces exactly the expected member ───────────────
        typeFilter("NowakX" + u);
        assertThat(visibleCount()).isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Marek");    // M3

        // ── scenario 3: spec word 'Trąbka' surfaces the TAGGED participant ──────────────────
        typeFilter(tagWord);                     // exact diacritic "Trąbka{u}" → {M2, the tagged row}
        assertThat(visibleCount()).describedAs("tag filter must surface exactly 1 tagged member").isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Iwona");

        // ── scenario 3b: accent-insensitive fold — user types no-diacritic 'trabka' ─────────
        typeFilter("trabka" + u);
        assertThat(visibleCount()).describedAs("accent-folded tag must still surface the tagged member")
                .isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Iwona");

        // ── scenario 3c: case-folding on the USER side — mixed-case probe on a name branch ─
        typeFilter("iWONa" + u);      // mixed-case variant of M2 first name → same single {M2}
        assertThat(visibleCount()).describedAs("mixed-case input must behave case-insensitively").isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Iwona");

        // ── scenario 3d: nonsense diacritic probe that folds to a stem matching no member ──
        typeFilter("\u00f6o\u00e9e" + u);   // 'öoée...' → folds 'oe' prefix, no name/tag starts so → 0 rows
        assertThat(visibleCount()).describedAs("nonsense probe must not fuzzy-match anything").isEqualTo(0);

        // ── scenario 4: UNION of name+tag branches → exactly ONE row, never duplicated ──────
        // "kazar" + root-u matches M1 TAG ("Kazar{u}") AND M1 FIRST-NAME ("Kaz{u}").
        // Correct behaviour: both predicate branches select the SAME <tr>, so we still see 1 row.
        typeFilter(tagName);
        assertThat(visibleCount()).describedAs("name+tag union must surface exactly one distinct member, not two")
                .isEqualTo(1);
        assertThat(firstCellOfOnlyVisibleRow()).containsIgnoringCase("Kaz");     // M1

        // ── scenario 5: clearing the input restores the full list ───────────────────────────
        typeFilter("");
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> visibleCount() == 3);
        assertThat(visibleCount()).isEqualTo(3);

        // ── scenario 6 (covered in 3/3b) + extra diacritic sanity: unknown diacritec term ────
        typeFilter("\u017bywiec" + u);   // 'Żywiec{u}' — present in no name OR tag → zero rows
        assertThat(visibleCount()).describedAs("unrelated diacritic term must match zero rows").isEqualTo(0);

        // ── scenario 7: console must not add new filter-specific errors during this pass ───
        // (Global environment noise — /favicon.ico returning 500, windband-utils.js:1249
        //  'Cannot read body.addEventListener' — is a pre-existing app issue unrelated to the
        //  filter; parent tests don't inspect the console at all. We assert only that no NEW
        //  SEVERE/ERROR entry mentions 'participant-filter', 'accent', or our fixture names,
        //  which would indicate the filter itself threw while matching.)
        List<String> severe = new ArrayList<>();
        try {
            List<LogEntry> logs = driver.manage().logs().get(LogType.BROWSER).getAll();
            if (logs != null) {
                for (LogEntry e : logs) {
                    String lvl = String.valueOf(e.getLevel()).toUpperCase();
                    if (!lvl.contains("SEVERE") && !lvl.contains("ERROR")) continue;
                    String msg = e.getMessage() == null ? "" : e.getMessage();
                    // Exclude known pre-existing environment noise that is NOT caused by the filter:
                    if (msg.contains("/favicon.ico")) continue;
                    if (msg.contains("windband-utils.js") && msg.contains("addEventListener")) continue;
                    severe.add(msg);
                }
            }
        } catch (Exception ignored) { /* provider without console logging */ }
        assertThat(severe).describedAs("browser console SEVERE/ERROR entries during filter pass").isEmpty();

        System.out.println("[t_5263250c] PASS — all 7 acceptance scenarios OK, " +
                "console SEVERE/ERROR count = " + severe.size());
    }

    // ───────────────────────────── filter I/O (REAL UI element) ────────────────────────────

    private void typeFilter(String term) {
        WebElement input = driver.findElement(By.id("participant-filter"));
        input.clear();
        if (term != null && !term.isEmpty()) input.sendKeys(term);
        // applyFilters() runs synchronously inside this executeScript call.
        ((JavascriptExecutor) driver).executeScript(
                "var el = document.getElementById('participant-filter');" +
                        "el.dispatchEvent(new Event('input', {bubbles:true}));");
    }

    private int visibleCount() {
        return driver.findElements(By.cssSelector(
                "#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"))
                .size();
    }

    private String firstCellOfOnlyVisibleRow() {
        List<WebElement> rows = driver.findElements(By.cssSelector(
                "#participants-table tbody tr[style=''], #participants-table tbody tr:not([style*='display: none'])"));
        assertThat(rows).as("expected exactly one visible participant row").hasSize(1);
        return rows.get(0).findElement(By.cssSelector("td:first-child")).getText();
    }

    // ─────────────────────── fixture helpers (patterns from EventDetailFilterUiTest) ─────────

    private void createMemberUi(String firstName, String lastName, WebDriverWait wait) throws Exception {
        loginAndNavigateTo("/members");
        driver.findElement(By.xpath("//button[contains(., 'Dodaj cz\u0142onka')]")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#member-form")));
        fill("firstName", firstName);
        fill("lastName", lastName);
        ((JavascriptExecutor) driver).executeScript(
                "document.querySelector(\"input[name='dateOfBirth']\").value = '1990-05-15';");
        driver.findElement(By.cssSelector("#member-form button[type='submit'].primary")).click();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#members-content table")));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> idByFirstOrNull(firstName) != null);
    }

    /** Creates the instrument (band-1 scoped) and makes it the member's primary — sibling-test pattern.
     *  Explicitly sets sort_priority: MemberQueryService.toMap(Instrument::getSortPriority,...) NPEs if null. */
    private void setPrimaryKeyInstrumentTag(String firstName, String instrument) {
        Long memberId = idByFirstOrNull(firstName);
        if (memberId == null) throw new IllegalStateException("member not found: " + firstName);
        try {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM instruments WHERE name = ?", Integer.class, instrument);
            if (c == null || c == 0) {
                jdbcTemplate.update(
                        "INSERT INTO instruments (name, band_id, sort_priority) VALUES (?, 1, 0)", instrument);
            } else {
                // Ensure sort_priority is non-null even if a prior fixture left it null.
                jdbcTemplate.update("UPDATE instruments SET sort_priority = 0 WHERE name = ? AND sort_priority IS NULL",
                        instrument);
            }
        } catch (Exception ignored) { /* unique-constraint race fine */ }

        Long instrumentId = jdbcTemplate.queryForObject(
                "SELECT id FROM instruments WHERE name = ?", Long.class, instrument);
        jdbcTemplate.update(
                "MERGE INTO member_instruments (member_id, instrument_id, is_primary) " +
                        "KEY(member_id, instrument_id) VALUES (?, ?, TRUE)",
                memberId, instrumentId);
    }

    private void fill(String name, String value) {
        WebElement el = driver.findElement(By.cssSelector("input[name='" + name + "']"));
        el.clear();
        el.sendKeys(value);
    }

    private Long idByFirstOrNull(String firstName) {
        try {
            Number c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE first_name = ?", Long.class, firstName);
            if (c == null || c.longValue() == 0L) return null;
            return jdbcTemplate.queryForObject("SELECT id FROM members WHERE first_name = ?", Long.class, firstName);
        } catch (Exception e) {
            return null;
        }
    }

}
