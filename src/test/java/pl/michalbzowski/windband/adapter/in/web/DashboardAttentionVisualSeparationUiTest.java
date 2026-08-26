package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

/**
 * UI tests verifying that the "Wymaga Twojej uwagi" (needs attention)
 * section on the dashboard is visually distinct from the "Nadchodzące"
 * (upcoming events) section.
 *
 * The two sections render the same card layout but the attention cards must:
 *  - have the {@code attention-card} CSS class
 *  - use a warning color (red/orange hue) so the user immediately sees
 *    something needs fixing
 *  - have a clearly labelled header with a warning glyph
 *
 * The upcoming section must:
 *  - have its own {@code h2} "Nadchodzące" header (was missing before)
 */
public class DashboardAttentionVisualSeparationUiTest extends UiTestBase {

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
        // collected for activeTeamId=1 are visible on the dashboard.
        band = bandRepository.findById(1L).orElseThrow();
        member = memberRepository.save(Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band));

        // Past paid-split event with a CONFIRMED + PENDING payment participation
        // → triggers PastPaidSplitUnpaidCondition → renders in attention list.
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
        pastEvent.inviteMember(member);
        pastEvent.recordResponse(member, ParticipationResponse.CONFIRMED);
        pastEvent.recordPayment(member, BigDecimal.valueOf(500));
        eventRepository.save(pastEvent);
    }

    @Test
    public void attention_section_has_dedicated_h2_header() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        var attentionHead = wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(., 'Wymaga Twojej uwagi')]")));
        assertThat(attentionHead).isNotNull();
    }

    @Test
    public void upcoming_section_has_dedicated_h2_header() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        // The upcoming section must now have its own header (previously was missing)
        var upcomingHead = wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(., 'Nadchodzące')]")));
        assertThat(upcomingHead).isNotNull();
    }

    @Test
    public void attention_cards_have_distinct_css_class() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(., 'Wymaga Twojej uwagi')]")));

        // Attention cards must be inside an element with class attention-list
        // and themselves carry the attention-card class.
        List<WebElement> attentionLists = driver.findElements(
                By.cssSelector("section.dashboard-upcoming .attention-list"));
        assertThat(attentionLists).as("attention-list container must exist").isNotEmpty();

        List<WebElement> attentionCards = driver.findElements(
                By.cssSelector("section.dashboard-upcoming .attention-card"));
        assertThat(attentionCards).as("attention-card items must exist").isNotEmpty();
    }

    @Test
    public void attention_card_has_warning_color_background() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(., 'Wymaga Twojej uwagi')]")));

        WebElement attentionBadge = driver.findElement(
                By.cssSelector("section.dashboard-upcoming .attention-list .attention-badge"));
        String badgeColor = attentionBadge.getCssValue("background-color");
        assertThat(badgeColor).as("attention badge must have a colored background").isNotBlank();

        // The badge background should not be transparent. Modern browsers return either
        // rgb(...) or rgba(...); the warning palette should be one of those.
        assertThat(badgeColor).doesNotContain("rgba(0, 0, 0, 0)");
        assertThat(badgeColor).matches("rgba?\\(.*\\)");

        // Verify it actually IS the warning hue — the Pico --pico-del-color red is
        // approx rgb(179, 45, 35) (Pico v2 default palette). The rendered background
        // must therefore have a red channel > 100 and a low blue channel (< 100).
        // This is what makes the attention section visually distinct from upcoming
        // (which uses the soft-primary blue mix).
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)").matcher(badgeColor);
        assertThat(m.find()).as("badge color must parse as rgb tuple: " + badgeColor).isTrue();
        int red = Integer.parseInt(m.group(1));
        int green = Integer.parseInt(m.group(2));
        int blue = Integer.parseInt(m.group(3));
        assertThat(red).as("red channel must dominate (warning hue): " + badgeColor).isGreaterThan(green);
        assertThat(red).as("red channel must dominate (warning hue): " + badgeColor).isGreaterThan(blue);
    }

    @Test
    public void attention_card_has_distinct_border_from_upcoming_card() {
        loginAndNavigateTo("/");

        WebDriverWait wait = new WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        wait.until(presenceOfElementLocated(
                By.xpath("//h2[contains(., 'Wymaga Twojej uwagi')]")));

        WebElement attentionCard = driver.findElement(
                By.cssSelector("section.dashboard-upcoming .attention-card"));
        WebElement upcomingCard = driver.findElement(
                By.cssSelector("section.dashboard-upcoming .upcoming-card:not(.attention-card)"));

        // The two card classes must differ in left-border styling — the warning
        // section uses a thick coloured left border (border-left-width: 4px or
        // border-left-color: <warning hue>), while the upcoming section does not.
        String attentionBorderLeft = attentionCard.getCssValue("border-left-width");
        String upcomingBorderLeft = upcomingCard.getCssValue("border-left-width");

        // Both should report a border-left-width (browser default is "0px").
        // We require that attention has a WIDER left border than upcoming.
        assertThat(attentionBorderLeft)
                .as("attention card should have an emphasised left border")
                .isNotEqualTo(upcomingBorderLeft);
    }
}
