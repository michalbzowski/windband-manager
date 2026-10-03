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

    /**
     * US-6.4 — the batch contract: one musician who plays parts in MORE THAN ONE setlist piece gets
     * a SINGLE e-mail carrying ALL of those parts, in concert (setlist) order, each with its own
     * independently-minted, resolvable token link. This is what "all parts for the event" means —
     * not one part per mail, and not ordered by member/part table id but by the US-7.2
     * {@code orderInSet} walk that the delivery read model is built on.
     */
    @Test
    void batchDelivery_memberPlayingTwoPieces_getsOneMailWithBoth_inConcertOrderEachResolving() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Instrument trumpet = instrumentRepository.save(Instrument.create("TrąbkaUS64" + suffix, band));

        Member basia = member(suffix, "BasiaUS64" + suffix, "basia64-" + suffix + "@test.com", true, trumpet);
        long basiaId = basia.getId();

        Composition firstPiece  = compositionRepository.save(Composition.create(
                "Otwarcie US-6.4 " + suffix, "opis", "Kompozytor", "Aranżer", band));
        Composition secondPiece = compositionRepository.save(Composition.create(
                "Zanim finał US-6.4 " + suffix, "opis", "Kompozytor2", "Aranżer2", band));

        // Part rows for the SAME musician on BOTH pieces — this is the batch.
        Long partA = partRepository.save(CompositionInstrument.forComposition(
                firstPiece, trumpet, "TrąbkaUS64" + suffix, 3, 7, null, PartSource.MANUAL, 1.0)).getId();
        Long partB = partRepository.save(CompositionInstrument.forComposition(
                secondPiece, trumpet, "TrąbkaUS64" + suffix, 2, 5, null, PartSource.MANUAL, 1.0)).getId();

        // Covering score files for both (pageCount >= pageTo), so neither trips the US-7.10 gate.
        scoreFileRepository.save(ScoreFile.forComposition(firstPiece, "application/pdf",
                "scoreA-" + suffix + ".pdf", 12_345L, sha256("A-" + suffix), "/srv/a.pdf", 9));
        scoreFileRepository.save(ScoreFile.forComposition(secondPiece, "application/pdf",
                "scoreB-" + suffix + ".pdf", 22_000L, sha256("B-" + suffix), "/srv/b.pdf", 9));

        // ONE new event on this band with BOTH pieces on the setlist — order pinned by orderInSet.
        // (Distinct from the seed event so the batch is unambiguous.)
        BandEvent event = eventRepository.save(BandEvent.create(
                "Koncert US-6.4 " + suffix, LocalDate.now().plusDays(30), LocalTime.of(19, 30),
                "Filharmonia", EventType.CONCERT, band, PaymentType.FREE, null));
        eventCompositionRepository.save(EventComposition.link(event, firstPiece,  1)); // position 1
        eventCompositionRepository.save(EventComposition.link(event, secondPiece, 2)); // position 2

        this.captured.reset();
        PartDeliveryResult result = service.deliverParts(event.getId(), band.getId(), "admin@test.com");

        // Exactly ONE envelope for the one musician who plays both pieces — no duplication, no split.
        assertThat(result.sent()).as("one mail per musician, not one mail per part").isEqualTo(1);
        PartDeliveryResult.Delivered d = result.delivered().get(0);
        assertThat(d.memberId()).isEqualTo(basiaId);
        assertThat(d.parts()).hasSize(2).as("both of the musician's parts travel in that single e-mail");

        // Concert order: the setlist position 1 piece comes before position 2, regardless of the
        // order I created/inserted the part rows above.
        PartDeliveryResult.PartDeliveryRow p0 = d.parts().get(0);
        PartDeliveryResult.PartDeliveryRow p1 = d.parts().get(1);
        assertThat(p0.pieceTitle()).contains("Otwarcie US-6.4");
        assertThat(p1.pieceTitle()).contains("Zanim finał US-6.4");

        // Each part carries its OWN live token link and they really are distinct, resolvable rows.
        assertThat(p0.publicPartLink()).isNotEqualTo(p1.publicPartLink());
        assertThat(storedTokenFor(p0.publicPartLink()).getPart().getId()).isEqualTo(partA);
        assertThat(storedTokenFor(p1.publicPartLink()).getPart().getId()).isEqualTo(partB);

        // Transport saw exactly one envelope, addressed to the right mailbox.
        assertThat(captured.mails).hasSize(1).first()
                .satisfies(m -> assertThat(m.to).isEqualTo(basia.getEmail()));
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
