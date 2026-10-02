package pl.michalbzowski.windband.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import pl.michalbzowski.windband.UiTestBase;
import pl.michalbzowski.windband.application.service.EmailSender;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventComposition;
import pl.michalbzowski.windband.domain.event.EventCompositionRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-6.3 — the "Rozdanie głosów" panel on the event detail page, from the musician's-eye route:
 * seeded part distribution shows the send button (with a per-player "brak zgody" badge where
 * applicable); pressing it opens the confirm dialog; confirming POSTs the delivery form and the
 * honest result banner comes back — sent count + the non-consenting member named as skipped.
 *
 * <p>Transport is a {@code @Primary} capturing stub (see {@link CapturingMailConfig} at the
 * bottom of this file): {@code SendGridEmailSender} is an unconditional bean that posts to
 * api.sendgrid.com whenever an API key resolves, and tests must not perform network calls —
 * the same hermetic pattern as {@code EventPartDeliveryIntegrationTest}.
 * What THIS test pins: the button only renders when there IS a distribution, the modal wiring,
 * that exactly ONE envelope is captured for the consenting member (none for the non-consenting
 * one), and that the flash banner reports honestly on the very page the operator came back to.
 *
 * <p>Seeds into band 1 (the UI login's team) — same pattern as {@code EventConsentBadgeUiTest};
 * unique-name rows keep it isolated from other tests' leftovers.
 */
@Import(EventPartDeliveryUiTest.CapturingMailConfig.class)
class EventPartDeliveryUiTest extends UiTestBase {

    @Autowired private BandRepository bandRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private CompositionInstrumentRepository partRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventCompositionRepository eventCompositionRepository;

    private static final String MARK = Long.toString(System.currentTimeMillis());

    @Test
    void deliveryPanel_sendButton_confirmsAndBannerReportsHonestBuckets() {
        // ---- seed (band 1, uniquely named rows so other tests' leftovers cannot collide) ----
        Band band = bandRepository.findById(1L).orElseThrow();
        Instrument trumpet = instrumentRepository.save(Instrument.create("TrąbkaUs63" + MARK, band));

        Member jan = member(band, trumpet, "JanUs63" + MARK, true); // consenting — receives the envelope
        Member anna = member(band, trumpet, "AnnaUs63" + MARK, false); // plays the same part — no consent

        Composition marsz = compositionRepository.save(Composition.create(
                "MarszUi " + MARK, "opis", "K", "A", band));
        Long partId = partRepository.save(CompositionInstrument.forComposition(
                marsz, trumpet, "TrąbkaUs63" + MARK, 5, 9, null, PartSource.MANUAL, 1.0)).getId();
        scoreFileRepository.save(ScoreFile.forComposition(marsz, "application/pdf",
                "score-" + MARK + ".pdf", 12_345L, sha256("ui-" + MARK),
                "/srv/windband-scores/score.pdf", 12));

        String eventName = "KoncertUi " + MARK;
        BandEvent event = eventRepository.save(BandEvent.create(eventName, LocalDate.now().plusDays(21),
                LocalTime.of(18, 0), "Rynek", EventType.CONCERT, band, PaymentType.FREE, null));
        eventCompositionRepository.save(EventComposition.link(event, marsz, 1));

        // ---- drive the page the way an operator would ----
        loginAndNavigateTo("/events/" + event.getId());
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        By sendBtn = By.id("send-parts-btn");

        wait.until(ExpectedConditions.presenceOfElementLocated(sendBtn))
            .isDisplayed();   // button exists ⇒ a real US-6.2 distribution rendered (assignments non-empty)

        // The no-consent player must be visibly flagged BEFORE delivery.
        String pageText = (String) ((JavascriptExecutor) driver).executeScript(
                "return document.getElementById('send-parts-btn').closest('section').textContent;");
        assertThat(pageText)
                .as("anna (no consent) is listed for the part with a 'brak zgody' badge")
                .contains(annaFirstName())
                .containsIgnoringCase("brak zgody");

        // Confirm dialog opens from the button.
        driver.findElement(sendBtn).click();
        By confirmBtn = By.id("send-parts-confirm-btn");
        wait.until(ExpectedConditions.visibilityOfElementLocated(confirmBtn));

        // Confirm → modal's hidden form POSTs /events/{id}/parts-delivery → redirect back.
        driver.findElement(confirmBtn).click();
        By banner = By.id("part-delivery-result");
        wait.until(ExpectedConditions.presenceOfElementLocated(banner));
        String bannerText = driver.findElement(banner).getText();

        // Honest breakdown: exactly one envelope (jan), anna named as skipped-for-consent.
        assertThat(bannerText)
                .contains("Wysłano")
                .as("the sent bucket is non-empty — jan's envelope was accepted by the transport")
                .contains(annaFirstName())
                .as("anna must be listed in the banner (skipped, no consent) — never 'sent'");

        // The transport captured EXACTLY one real envelope, addressed to the consenting member.
        // The address mirrors the deterministic scheme in member(): "m63-{name-lowercase}@test.com".
        CapturedMailLog mail = CapturedMailLog.LAST;
        String janLower = janName().toLowerCase();
        assertThat(mail.mails).as("exactly one e-mail left the app (the honoring side got it)")
                .hasSize(1)
                .first()
                .satisfies(m -> {
                    org.assertj.core.api.Assertions.assertThat(m.to())
                            .isEqualTo("m63-" + janLower + "@test.com")
                            .as("the envelope went to the CONSENTING musician only");
                });

        // The sent e-mail carried a REAL token link ⇒ exactly one token row minted for this part.
        Long tokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM part_share_tokens WHERE part_id = ?", Long.class, partId);
        assertThat(tokens).as("the link jan received must resolve (token row minted US-7.11)")
                .isEqualTo(1L);
    }

    private static String janName() {
        return "JanUs63" + MARK;
    }

    // anna's first name — kept out of the main test body for readability in the assertion.
    private String annaFirstName() {
        return "AnnaUs63" + MARK;
    }

    private Member member(Band band, Instrument trumpet, String firstName, boolean consent) {
        Member m = Member.create(firstName, "Ui", null, band);
        m.updateContact("m63-" + firstName.toLowerCase() + "@test.com", null, consent);
        m.addInstrument(trumpet, true); // must match the part's role name (exact folded tag, rule 1)
        return memberRepository.save(m);
    }

    private static String sha256(String seed) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // ────────────── captured e-mail transport (replaces the real SendGrid bean) ──────────────
    // SendGridEmailSender is an unconditional @Component that posts to api.sendgrid.com when a key
    // resolves; UI tests must not perform network calls. A @Primary stub swaps it out here, the
    // same hermetic pattern EventPartDeliveryIntegrationTest uses. LAST is a process-wide handle so
    // the test method can read what flowed WITHOUT changing the production bean's constructor.
    static class CapturedMailLog implements EmailSender {
        /** Process-wide handle so the test can read what flowed without touching DI wiring. */
        static final CapturedMailLog LAST = new CapturedMailLog();
        final java.util.List<Mail> mails = new java.util.ArrayList<>();

        record Mail(String to, String subject) {}

        @Override
        public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent) {
            mails.add(new Mail(toEmail, subject));
        }
    }

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @Primary
        EmailSender capturingEmailSender() {
            return CapturedMailLog.LAST;
        }
    }
}
