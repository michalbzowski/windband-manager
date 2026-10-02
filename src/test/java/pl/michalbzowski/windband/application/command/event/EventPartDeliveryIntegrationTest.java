package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.service.EmailSender;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.PartShareToken;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-6.3 — delivery of an event's parts to its musicians, over a REAL Spring context and a REAL
 * database (profile {@code test}: H2 in PostgreSQL mode with {@code open-in-view: false} — the
 * exact configuration production runs under).
 *
 * <p><b>Why this IT exists beyond the unit suite:</b>
 * <ul>
 *   <li>The delivery service resolves band/event data through real, database-backed LAZY proxies.
 *       Mockito never opens an EntityManager, so a lazy association read outside a transaction
 *       (e.g. {@code BandEvent.band} when no HTTP session/tx is alive) could not have blown up in
 *       unit tests. {@code deliverParts(...) completing at all} here is the regression guard.</li>
 *   <li>The covering-file gate ({@code PartLinkQueryService.open} → {@code NoCoveringFileException})
 *       reads a real {@code score_files} row — pinned where its data actually lives.</li>
 *   <li>E-mail transport is captured by a {@link CapturedMailLog} bean, so assertions are about
 *       WHO received WHAT and whether the link really resolves into a DB token row. The stock
 *       {@code SendGridEmailSender} would silently no-op here (no API key), making "sent"
 *       indistinguishable from "refused".</li>
 * </ul>
 *
 * <p>Unit-level contracts (buckets, ordering, fail-closed) stay in
 * {@link EventPartDeliveryCommandServiceTest}.
 */
@Import(EventPartDeliveryIntegrationTest.CapturingMailConfig.class)
class EventPartDeliveryIntegrationTest extends BaseIntegrationTest {

    @Autowired private BandRepository bandRepository;
    @Autowired private InstrumentRepository instrumentRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private CompositionRepository compositionRepository;
    @Autowired private CompositionInstrumentRepository partRepository;
    @Autowired private ScoreFileRepository scoreFileRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventCompositionRepository eventCompositionRepository;
    @Autowired private PartShareTokenRepository tokenRepository;
    @Autowired private EventPartDeliveryCommandService service;
    @Autowired private CapturedMailLog captured;

    private Band band;
    private Member jan;   // consenting trumpet player — must receive exactly one envelope
    private Member anna;  // unconsented, same part — must be silently gated
    private Composition marsz;
    private Long eventId;
    private Long partId;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        band = bandRepository.save(Band.create("Orkiestra US63 " + suffix, "us63-" + suffix));
        Instrument trumpet = instrumentRepository.save(Instrument.create("Trąbka", band));

        jan = member(suffix, "JanUS63" + suffix, "jan63-" + suffix + "@test.com", true, trumpet);
        anna = member(suffix, "AnnaUS63" + suffix, "anna63-" + suffix + "@test.com", false, trumpet);

        marsz = compositionRepository.save(Composition.create(
                "Marsz US-6.3 " + suffix, "opis", "Kompozytor", "Aranżer", band));

        // Part "Trąbka" pp.5–9; BOTH members match it (rule 1: exact folded-name match), so the
        // consent gate below is the only thing keeping anna out of the mail.
        CompositionInstrument part = partRepository.save(CompositionInstrument.forComposition(
                marsz, trumpet, "Trąbka", 5, 9, null, PartSource.MANUAL, 1.0));

        // Covering score file: pageCount 12 ≥ pageTo 9 (US-7.10's gate reads this row).
        scoreFileRepository.save(ScoreFile.forComposition(marsz, "application/pdf",
                "score-" + suffix + ".pdf", 12_345L, sha256("cover-" + suffix),
                "/srv/windband-scores/score.pdf", 12));

        BandEvent event = eventRepository.save(BandEvent.create(
                "Koncert US-6.3 " + suffix, LocalDate.now().plusDays(21), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null));
        eventCompositionRepository.save(EventComposition.link(event, marsz, 1));

        this.eventId = event.getId();
        this.partId = part.getId();
        this.captured.reset();
    }

    @Test
    void delivery_deliversOneEnvelopeToConsentingMember_andMintsARealTokenLink() {
        PartDeliveryResult result = service.deliverParts(eventId, band.getId(), "admin@test.com");

        assertThat(result.sent()).as("exactly one envelope — the consenting member").isEqualTo(1);
        assertThat(result.failedSend()).isEmpty();

        PartDeliveryResult.Delivered d = result.delivered().get(0);
        assertThat(d.email()).isEqualTo(jan.getEmail());
        assertThat(d.parts()).hasSize(1).first().satisfies(row -> {
            assertThat(row.role()).isEqualTo("Trąbka");
            assertThat(row.pageFrom()).isEqualTo(5);
            assertThat(row.pageTo()).isEqualTo(9);
            assertThat(row.publicPartLink())
                    .as("link is the US-7.11 token form — never enumerable ids")
                    .startsWith("http://localhost:8080/public/parts/");
        });

        // Captured envelope: the real send path ran, the body was really template-rendered.
        assertThat(captured.mails).hasSize(1);
        CapturedMailLog.Mail mail = captured.mails.get(0);
        assertThat(mail.to).isEqualTo(jan.getEmail());
        assertThat(mail.subject).contains("Koncert US-6.3");
        assertThat(mail.html).contains("Trąbka").contains("Marsz US-6.3");

        // The link must RESOLVE — the token really lives in the database, is not a dead string.
        PartShareToken stored = storedTokenFor(d.parts().get(0).publicPartLink());
        assertThat(stored.getPart().getId()).isEqualTo(partId);
    }

    @Test
    void consentGate_memberWithoutConsent_isSilentlySkipped_andNoTokenIsMinted() {
        jan.deactivate(); // only anna's (unconsented) row remains — persist it: merge() doesn't
        // auto-flush a detached entity's field change, and the query reads the live table.
        memberRepository.save(jan);

        PartDeliveryResult result = service.deliverParts(eventId, band.getId(), "admin@test.com");

        assertThat(result.sent()).isZero();
        assertThat(result.skippedNoConsent())
                .as("anna lands in the honest 'no consent' bucket")
                .anyMatch(s -> s.startsWith("AnnaUS63"));
        assertThat(captured.mails).as("consent refusal is SILENT — no envelope").isEmpty();
        assertThat(tokenRepository.findByPartId(partId))
                .as("gated members never trigger token minting (fail-closed gate ordering)")
                .isEmpty();
    }

    @Test
    void coveringFileGate_tooShortScoreFile_refusedHonestAndNeverMailed() {
        // Swap in a too-small score file: pageCount 3 < part pageFrom 5.
        // (ScoreFileRepository exposes single delete only — drop what we seeded, add the short one.)
        scoreFileRepository.findAllByComposition(marsz).forEach(scoreFileRepository::delete);
        scoreFileRepository.save(ScoreFile.forComposition(marsz, "application/pdf",
                "short.pdf", 321L, sha256("short"), "/srv/windband-scores/short.pdf", 3));

        PartDeliveryResult result = service.deliverParts(eventId, band.getId(), "admin@test.com");

        assertThat(result.sent()).isZero();
        assertThat(result.noScoreFile())
                .as("refusal surfaces in its own honest bucket — 'brak pliku', not 'sent'")
                .anyMatch(s -> s.contains("Trąbka"));
        assertThat(captured.mails).isEmpty();
    }

    @Test
    void unknownEvent_failsClosedWithNotFound() {
        assertThatThrownBy(() -> service.deliverParts(987_650_001L, band.getId(), "admin@test.com"))
                .isInstanceOf(EventNotFoundException.class);
        assertThat(captured.mails).isEmpty();
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private Member member(String suffix, String firstName, String email, boolean consent, Instrument trumpet) {
        Member m = Member.create(firstName, "US63", null, band);
        m.updateContact(email, null, consent);
        m.addInstrument(trumpet, true);
        return memberRepository.save(m);
    }

    private PartShareToken storedTokenFor(String publicLink) {
        String tokenStr = publicLink.substring(publicLink.length() - 36);
        UUID token = UUID.fromString(tokenStr); // malformed form fails the test
        return tokenRepository.findByToken(token)
                .orElseThrow(() -> new AssertionError("link carries a token with no DB row"));
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

    // ────────────── captured e-mail transport (replaces the no-op SendGrid bean) ──────────────

    static class CapturedMailLog implements EmailSender {
        final List<Mail> mails = new ArrayList<>();

        record Mail(String to, String subject, String html) {}

        @Override
        public void sendHtmlEmail(String toEmail, String toName, String subject, String htmlContent) {
            mails.add(new Mail(toEmail, subject, htmlContent));
        }

        void reset() {
            mails.clear();
        }
    }

    @TestConfiguration
    static class CapturingMailConfig {
        @Bean
        @Primary
        CapturedMailLog capturingEmailSender() {
            return new CapturedMailLog();
        }
    }
}
