package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.member.ConsentType;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI regression for the "Zgoda na informacje" column on
 * {@code events/detail.html}. Two seed members ({@code Jan Kowalski} and
 * {@code Anna Nowak}) are invited to a fresh event; the test grants EVENTS
 * consent for Jan and leaves Anna without consent, then asserts the
 * rendered badges match the persisted state.
 *
 * <p>Companion tests:
 * <ul>
 *   <li>{@code NotificationSenderConsentTest} — unit-level, isolated.</li>
 *   <li>{@code EventConsentIntegrationTest} — full Spring context, real DB,
 *       real NotificationSender, fake Channel.</li>
 * </ul>
 */
class EventConsentBadgeUiTest extends UiTestBase {

    @Test
    void shouldRenderConsentBadgePerParticipantOnEventDetail() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // 0. Log in (UI test must be authenticated before any /api call).
        loginAndNavigateTo("/events");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-list-container")));

        // 1. Grant EVENTS consent for Jan (member_id=1) and explicitly deny for
        //    Anna (member_id=2). member_consents is TRUNCATEd by cleanDatabase(),
        //    so these rows are the only consent state for the test — but we
        //    verify PER member, not globally, because earlier UI tests in the
        //    suite may have left behind unrelated consent rows (MemberWelcomeService
        //    inserts 3 default-deny rows per new member; cleanDatabase then runs
        //    in @BeforeEach, but it is not a guarantee we can assert on).
        jdbcTemplate.update(
                "INSERT INTO member_consents (member_id, consent_type, granted, granted_at) "
                        + "VALUES (?, ?, ?, ?)",
                1L, ConsentType.EVENTS.name(), true, java.time.Instant.now());
        jdbcTemplate.update(
                "INSERT INTO member_consents (member_id, consent_type, granted, granted_at) "
                        + "VALUES (?, ?, ?, ?)",
                2L, ConsentType.EVENTS.name(), false, null);
        // sanity — check the per-member state we just wrote, not a global count
        Boolean janEventsGranted = jdbcTemplate.queryForObject(
                "SELECT granted FROM member_consents WHERE member_id = ? AND consent_type = ?",
                Boolean.class, 1L, ConsentType.EVENTS.name());
        Boolean annaEventsGranted = jdbcTemplate.queryForObject(
                "SELECT granted FROM member_consents WHERE member_id = ? AND consent_type = ?",
                Boolean.class, 2L, ConsentType.EVENTS.name());
        assertThat(janEventsGranted).as("Jan's EVENTS consent must be true").isTrue();
        assertThat(annaEventsGranted).as("Anna's EVENTS consent must be false").isFalse();

        // 2. Create a fresh event via the API (faster and more deterministic
        //    than driving the create form).
        String eventName = "Koncert z consentem " + System.currentTimeMillis();
        Long eventId = createEventViaApi(eventName);

        // 3. Invite both members via the API (same pattern as
        //    EventParticipationInstrumentUiTest — avoids HTMX click race).
        inviteMemberViaApi(eventId, 1L);
        inviteMemberViaApi(eventId, 2L);

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Integer rows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM event_participations WHERE event_id = ?",
                    Integer.class, eventId);
            assertThat(rows).as("Both members should be event participants").isEqualTo(2);
        });

        // 4. Navigate to the event detail page directly (no list-row click).
        driver.get(baseUrl() + "/events/" + eventId);
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("events-content")));
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(
                By.cssSelector("#participants-table tbody tr"), 1));

        // 5. Assert the rendered "Zgoda na informacje" cells. The template
        //    renders th:data-consent-given on .consent-cell, which we use
        //    as the contract — never match the raw emoji or polish text.
        WebElement janConsentCell = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".consent-cell[data-member-id='1']")));
        WebElement annaConsentCell = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector(".consent-cell[data-member-id='2']")));

        assertThat(janConsentCell.getAttribute("data-consent-given"))
                .as("Jan's badge must reflect granted=true")
                .isEqualTo("true");
        assertThat(janConsentCell.getText())
                .as("Jan's badge text must read 'Wyraził zgodę'")
                .contains("Wyraził zgodę");

        assertThat(annaConsentCell.getAttribute("data-consent-given"))
                .as("Anna's badge must reflect granted=false")
                .isEqualTo("false");
        assertThat(annaConsentCell.getText())
                .as("Anna's badge text must read 'Brak zgody'")
                .contains("Brak zgody");
    }

    private Long createEventViaApi(String name) {
        // Return just the status + id — keep parsing in Java trivial.
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

    private void inviteMemberViaApi(Long eventId, Long memberId) {
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
