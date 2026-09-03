package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Consent;
import pl.michalbzowski.windband.domain.member.ConsentRepository;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI regression for the merged {@code Powiadomienie} column on the Event
 * Details view ({@code events/detail.html}). The old "Status wysyłki" and
 * "Zgoda na informacje" columns are replaced by a single cell driven by this
 * priority order:
 *
 * <ol>
 *   <li>No consent for notification         → ❌ Brak zgody</li>
 *   <li>Consent given + send attempt FAILED → ⚠️ Błąd wysyłki (warning triangle)</li>
 *   <li>Consent given + the send SUCCEEDED  → ✅ Wysłano</li>
 *   <li>Consent given + no send attempt yet → 📭 Nie wysłano</li>
 * </ol>
 *
 * <p>This drives all four states deterministically WITHOUT a real email channel
 * (no SendGrid in CI): consent is set via {@code member_consents} rows and the
 * four notification outcomes are produced by inserting controlled rows into
 * {@code event_invitations} (status NOT_SENT / SENT / FAILED).
 */
class EventConsentBadgeUiTest extends UiTestBase {

    @Autowired private MemberRepository memberRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private BandRepository bandRepository;

    @Test
    void shouldRenderMergedPowiadomienieColumnPerState() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // 0. Log in (UI test must be authenticated before any /api call).
        loginAndNavigateTo("/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-list-container")));

        // 1. Consent setup:
        //    - Jan   (member_id=1, seeded) : consent GRANTED  → covers consent-given paths (NOT_SENT, FAILED)
        //    - Anna  (member_id=2, seeded) : consent DENIED   → covers "no consent" path
        //    - two fresh members: consent GRANTED             → covers SENT and (re-assert) FAILED
        jdbcTemplate.update(
                "INSERT INTO member_consents (member_id, consent_type, granted, granted_at) VALUES (?, ?, ?, ?)",
                1L, ConsentType.EVENTS.name(), true, java.time.Instant.now());
        // Grant consent to the two freshly created members below.
        Band band = bandRepository.findById(1L).orElseThrow();
        Member sentMember  = createConsentingMember("Wyslany",  band);   // will be SENT
        Member failedExtra = createConsentingMember("Bledny",    band); // extra FAILED (belt & suspenders)

        // 2. Create a fresh event via the API (deterministic, faster than the form).
        String eventName = "Powiadomienie merge " + System.currentTimeMillis();
        Long eventId = createEventViaApi(eventName);

        // 3. Invite every subject member via the API (creates an event_invitations
        //    row with status NOT_SENT). This gives us a NOT_SENT row for Jan,
        //    Anna, sentMember (no consent yet set on Anna) and failedExtra.
        for (long mid : new long[] {1L, 2L, sentMember.getId(), failedExtra.getId()}) {
            inviteMemberViaApi(eventId, mid);
        }

        // 4. Deterministically steer each member into one of the four branches:
        //     - Jan         : keep NOT_SENT + consent=true  → "Nie wysłano" (📭)
        //     - Anna        : consent=false                 → "Brak zgody"  (❌)
        //     - sentMember  : force SENT                    → "Wysłano"     (✅)
        //     - failedExtra : force FAILED                  → "Błąd wysyłki"(⚠️)
        jdbcTemplate.update("UPDATE event_invitations SET status = 'SENT',   sent_at = CURRENT_TIMESTAMP WHERE event_id = ? AND member_id = ?", eventId, sentMember.getId());
        jdbcTemplate.update("UPDATE event_invitations SET status = 'FAILED'                                 WHERE event_id = ? AND member_id = ?", eventId, failedExtra.getId());

        // 5. Navigate to the event detail page directly (no list-row click).
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        wait.until(ExpectedConditions.numberOfElementsToBe(
                By.cssSelector("#participants-table tbody tr"), 4));

        // 6. Assert each merged "Powiadomienie" cell by member id — all four states:
        verifyCell(driver, 1L,                 "Nie wysłano");   // NOT_SENT  + consent → 📭
        verifyCell(driver, sentMember.getId(), "Wysłano");       // SENT      + consent → ✅
        verifyCell(driver, failedExtra.getId(), "Błąd wysyłki"); // FAILED    + consent → ⚠️
        verifyCell(driver, 2L,                 "Brak zgody");     // no consent         → ❌

        // Extra: explicitly assert the failure uses a WARNING TRIANGLE (⚠), never a red cross.
        WebElement failedCell = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(String.format(".powiadomienie-cell[data-member-id='%d']", failedExtra.getId()))));
        assertThat(failedCell.getText())
                .as("Failure cell must contain the warning-triangle glyph")
                .contains("⚠");
        assertThat(failedCell.getText())
                .as("Failure cell must NOT use the red 'Brak zgody' label")
                .doesNotContain("Brak zgody");

        // 7. The header row advertises exactly one "Powiadomienie" column — the old
        //    "Status wysyłki" + "Zgoda na informacje" pair is gone.
        assertThat(driver.findElements(By.xpath("//th[normalize-space(text()) = 'Powiadomienie']")).size())
                .as("Exactly one 'Powiadomienie' header must exist")
                .isEqualTo(1);
        assertThat(driver.findElements(By.xpath("//th[normalize-space(text()) = 'Status wysyłki']")).size())
                .as("'Status wysyłki' header must be removed")
                .isZero();
        assertThat(driver.findElements(By.xpath("//th[normalize-space(text()) = 'Zgoda na informacje']")).size())
                .as("'Zgoda na informacje' header must be removed")
                .isZero();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Member createConsentingMember(String name, Band band) {
        Member m = Member.create("Test", name + " " + System.nanoTime(), LocalDate.of(1990, 1, 1), band);
        m.updateContact(name.toLowerCase().replace(" ", ".") + "@test.com", "500000000", false);
        Member saved = memberRepository.save(m);
        Consent consent = Consent.create(saved, ConsentType.EVENTS);
        consent.grant();
        consentRepository.save(consent);
        return saved;
    }

    private void verifyCell(WebDriver driver, long memberId, String expectedText) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        WebElement cell = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(String.format(".powiadomienie-cell[data-member-id='%d']", memberId))));
        assertThat(cell)
                .as("Merged Powiadomienie cell for member %d must exist", memberId)
                .isNotNull();
        String text = cell.getText().trim();
        assertThat(text)
                .as("Member %d must render '%s' (got: '%s') in the merged Powiadomienie cell",
                        memberId, expectedText, text)
                .contains(expectedText);
    }

    private Long createEventViaApi(String name) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "xhr.send(JSON.stringify({" +
                "  name: arguments[0]," +
                "  date: arguments[1]," +
                "  startTime: '18:00'," +
                "  location: 'Sala Testowa'," +
                "  eventType: 'CONCERT'," +
                "  paymentType: 'FREE'" +
                "}));" +
                "var body = JSON.parse(xhr.responseText);" +
                "return xhr.status + ':' + body.id;",
                name, LocalDate.now().plusDays(10).toString());
        String[] parts = ((String) result).split(":", 2);
        int status = Integer.parseInt(parts[0]);
        assertThat(status).as("Event creation must return 201").isEqualTo(201);
        return Long.valueOf(parts[1]);
    }

    private void inviteMemberViaApi(Long eventId, long memberId) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "var xhr = new XMLHttpRequest();" +
                "xhr.open('POST', '/api/events/' + arguments[0] + '/invite', false);" +
                "xhr.setRequestHeader('Content-Type', 'application/json');" +
                "xhr.send(JSON.stringify({eventId: parseInt(arguments[0]), memberId: parseInt(arguments[1])}));" +
                "return xhr.status;",
                eventId, String.valueOf(memberId));
        assertThat(result)
                .as("Invite API must return 200 (got: " + result + ")")
                .isEqualTo(200L);
    }
}
