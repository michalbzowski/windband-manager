package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.event.ParticipationResponse;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

/**
 * UI tests for attention list on dashboard.
 * Verifies that attention items appear correctly based on conditions.
 */
public class DashboardAttentionListUiTest extends UiTestBase {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    private Band band;
    private Member member;

    @BeforeEach
    public void prepareData() {
        // Reuse the seeded Test Band (id=1) — admin has team_id=1, so attention items
        // collected for activeTeamId=1 are visible on the dashboard. UiTestBase.cleanDatabase()
        // does NOT truncate `bands` (seed baseline), so reusing keeps slug/name uniqueness intact.
        band = bandRepository.findById(1L).orElseThrow();
        member = memberRepository.save(Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band));

        // Create a past paid-split event
        BandEvent pastEvent = BandEvent.create(
                "Past Concert",
                LocalDate.now().minusDays(5),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(500)
        );
        pastEvent = eventRepository.save(pastEvent);

        // Invite member, record CONFIRMED response + PENDING payment → triggers attention condition
        pastEvent.inviteMember(member);
        pastEvent.recordResponse(member, ParticipationResponse.CONFIRMED);
        pastEvent.recordPayment(member, BigDecimal.valueOf(500));
        eventRepository.save(pastEvent);
    }

    @Test
    public void dashboard_displays_attention_list_for_past_paid_event_with_unpaid() {
        // Act
        loginAndNavigateTo("/");

        // Assert: attention list section is visible
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        var attentionListContainer = wait.until(presenceOfElementLocated(By.xpath("//h2[contains(text(), 'Wymaga Twojej uwagi')]")));
        assertThat(attentionListContainer).isNotNull();

        // Assert: attention item is present with correct details
        var attentionCard = wait.until(presenceOfElementLocated(By.xpath("//a[contains(@href, '/events/') and .//h3[text()='Past Concert']]")));
        assertThat(attentionCard).isNotNull();

        // Assert: danger icon is displayed
        var dangerIcon = attentionCard.findElement(By.xpath(".//div[contains(text(), '\uD83D\uDEA8')]"));
        assertThat(dangerIcon).isNotNull();

        // Assert: subtitle indicates payment issue
        var subtitle = attentionCard.findElement(By.xpath(".//*[contains(text(), 'Wypłata')]"));
        // Use textContent attribute to get the raw text even when the element
        // is hidden or only present in the DOM tree. innerText (getText) can
        // return "" when the element has only inline children or no visible content
        // in headless Chrome.
        String subtitleText = subtitle.getAttribute("textContent");
        if (subtitleText == null || subtitleText.isBlank()) {
            subtitleText = subtitle.getText();
        }
        assertThat(subtitleText).contains("Wypłata nie została rozdysponowana");
    }

    @Test
    public void dashboard_ignores_free_events_for_attention() {
        // Arrange: create a FREE event (not PAID_SPLIT) with unpaid participation
        BandEvent freeEvent = BandEvent.create(
                "Free Concert",
                LocalDate.now().minusDays(3),
                LocalTime.of(19, 0),
                "Park",
                EventType.CONCERT,
                band,
                PaymentType.FREE,
                BigDecimal.ZERO
        );
        freeEvent = eventRepository.save(freeEvent);

        freeEvent.inviteMember(member);
        freeEvent.recordResponse(member, ParticipationResponse.CONFIRMED);
        freeEvent.recordPayment(member, BigDecimal.valueOf(100));
        eventRepository.save(freeEvent);

        // Act
        loginAndNavigateTo("/");

        // Assert: attention list should not mention the free event (only the past paid-split one)
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(5));
        var eventNames = driver.findElements(By.xpath("//a[contains(@href, '/events/')]//*[contains(text(), 'Free Concert')]"));
        assertThat(eventNames).isEmpty();
    }

    @Test
    public void dashboard_ignores_declined_participations_for_attention() {
        // Arrange: create a PAID_SPLIT event with DECLINED participation + unpaid status
        BandEvent paidEvent = BandEvent.create(
                "Declined Event",
                LocalDate.now().minusDays(2),
                LocalTime.of(20, 0),
                "Venue",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(300)
        );
        paidEvent = eventRepository.save(paidEvent);

        paidEvent.inviteMember(member);
        paidEvent.recordResponse(member, ParticipationResponse.DECLINED);
        paidEvent.recordPayment(member, BigDecimal.valueOf(100));
        eventRepository.save(paidEvent);

        // Act
        loginAndNavigateTo("/");

        // Assert: attention list should not mention the declined event
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(5));
        var declinedEventNames = driver.findElements(By.xpath("//a[contains(@href, '/events/')]//*[contains(text(), 'Declined Event')]"));
        assertThat(declinedEventNames).isEmpty();
    }

    @Test
    public void dashboard_attention_item_is_clickable() {
        // Act
        loginAndNavigateTo("/");

        // Find and click the attention item
        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        var attentionLink = wait.until(presenceOfElementLocated(By.xpath("//a[contains(@href, '/events/') and .//h3[text()='Past Concert']]")));

        // Click using JS to avoid ElementClickInterceptedException
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", attentionLink);

        // Assert: navigated to event detail page
        wait.until(d -> d.getCurrentUrl().contains("/events/"));
        assertThat(driver.getCurrentUrl()).containsIgnoringCase("/events/");
    }
}
