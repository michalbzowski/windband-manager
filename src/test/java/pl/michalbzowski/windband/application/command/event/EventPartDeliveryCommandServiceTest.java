package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import pl.michalbzowski.windband.application.command.composition.PartShareTokenCommandService;
import pl.michalbzowski.windband.application.command.event.PartDeliveryResult.PartDeliveryRow;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService.NoCoveringFileException;
import pl.michalbzowski.windband.application.query.composition.PartLinkQueryService.PartLink;
import pl.michalbzowski.windband.application.query.event.EventCompositionPartsQueryService;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.Distribution;
import pl.michalbzowski.windband.application.dto.event.EventPartDistributionDto.PartAssignment;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.application.service.EmailSender;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventPartDelivery;
import pl.michalbzowski.windband.domain.event.EventRepository;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * US-6.3 — delivering an event's parts to its musicians, unit-level contract.
 *
 * <p>Pinned behaviour:
 * <ol>
 *   <li>happy path — ONE e-mail per member containing every part they play, each with its own
 *       public token link (US-7.11) and page range (asserted on the template {@link Context} the
 *       service hands to the renderer — the rendered HTML itself is covered by the IT/UI tests);</li>
 *   <li>consent gate — a member without {@code emailConsentGiven} is skipped SILENTLY (no token
 *       minted, no covering-file lookup, no envelope, no event lookup);</li>
 *   <li>no e-mail address — skipped and reported, never a hard failure of the batch;</li>
 *   <li>no covering score file — the part is refused (US-7.10's gate) and reported, the link is
 *       never minted for it;</li>
 *   <li>best-effort — a single failed envelope does not cancel the rest; only when EVERY envelope
 *       fails do we rethrow so the UI can surface "nie udało się";</li>
 *   <li>fail-closed — unknown band / foreign event propagate without touching any collaborator.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class EventPartDeliveryCommandServiceTest {

    private static final Long EVENT_ID = 55L;
    private static final Long BAND_ID = 7L;
    private static final Long PIECE_ID = 900L;
    private static final String BASE_URL = "http://band.example";
    private static final String PIECE_TITLE = "Marsz";
    private static final String EVENT_NAME = "Koncert Charytatywny";

    @Mock private EventCompositionPartsQueryService partsQueryService;
    @Mock private PartLinkQueryService partLinkQueryService;
    @Mock private PartShareTokenCommandService tokenService;
    @Mock private EmailSender emailSender;
    @Mock private SpringTemplateEngine templateEngine;
    @Mock private BandQueryService bandQueryService;
    @Mock private MemberRepository memberRepository;
    @Mock private EventRepository eventRepository;
    @Mock private pl.michalbzowski.windband.domain.event.EventPartDeliveryRepository auditRepository;

    @Captor private ArgumentCaptor<String> subjectCaptor;
    @Captor private ArgumentCaptor<Context> contextCaptor;

    private EventPartDeliveryCommandService service;
    private Band band;
    private BandEvent event;

    @BeforeEach
    void setUp() {
        service = new EventPartDeliveryCommandService(
                partsQueryService, partLinkQueryService, tokenService, emailSender,
                templateEngine, bandQueryService, memberRepository, eventRepository, auditRepository, BASE_URL);
        band = Band.create("Orkiestra Testowa", "orkiestra-testowa");
        event = BandEvent.create(EVENT_NAME, LocalDate.now().plusDays(7), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null);
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private static Member member(long id, String first, String last, String email) {
        Member m = Member.create(first, last, null, Band.create("Orkiestra Testowa", "orkiestra-testowa"));
        m.updateContact(email, null, true);
        setId(m, id);
        return m;
    }

    private static void setId(Member m, long id) {
        try {
            Field f = Member.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PartAssignment row(long memberId, String name, boolean consent, long partId,
                                      String role, int from, int to) {
        return new PartAssignment(memberId, name, consent, PIECE_ID, PIECE_TITLE, 1, partId, role,
                from, to, null, true, "Kornet");
    }

    private static PartLink link(long partId, String role, int from, int to) {
        return new PartLink(partId, PIECE_ID, BAND_ID, 5L, "score.pdf", "application/pdf",
                12_345L, PIECE_TITLE, role, from, to);
    }

    private Distribution distribution(PartAssignment... rows) {
        return new Distribution(List.of(), List.of(rows), List.of());
    }

    private void stubEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    protected void stubBand() {
        when(bandQueryService.getRequiredBand(BAND_ID)).thenReturn(band);
    }

    private void stubTemplate() {
        when(templateEngine.process(eq("email/event-part"), any())).thenReturn("<html>rendered</html>");
    }

    @SuppressWarnings("unchecked")
    private static List<PartDeliveryRow> ctxParts(Context ctx) {
        return (List<PartDeliveryRow>) ctx.getVariable("parts");
    }

    // ─────────────────────────────── happy path ───────────────────────────────

    @Test
    void happyPath_oneMailPerMember_allTheirPartsWithTokenLinks() {
        UUID tokenBrass = UUID.randomUUID();
        UUID tokenDrum = UUID.randomUUID();

        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        Member piotr = member(103L, "Piotr", "Zalewski", null);
        Member kasia = member(104L, "Kasia", "Wilk", "kasia@test.com");
        // Jan + Piotr + Kasia are looked up (consent OK); Anna is NOT — the consent gate stops
        // before any member lookup (asserted via the absence of a findById(102) call below).
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(memberRepository.findById(103L)).thenReturn(Optional.of(piotr));
        when(memberRepository.findById(104L)).thenReturn(Optional.of(kasia));

        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(partLinkQueryService.open(1101L, PIECE_ID, BAND_ID)).thenReturn(link(1101L, "Bęben", 4, 6));
        doThrow(new NoCoveringFileException("brak pliku dla stron 7–9"))
                .when(partLinkQueryService).open(1102L, PIECE_ID, BAND_ID);

        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(tokenBrass);
        when(tokenService.tokenFor(1101L, "band:7")).thenReturn(tokenDrum);
        stubEvent();
        stubBand();
        stubTemplate();
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID)).thenReturn(distribution(
                row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3),
                row(101L, "Jan Kowalski", true, 1101L, "Bęben", 4, 6),
                row(102L, "Anna Nowak", false, 1101L, "Bęben", 4, 6),
                row(103L, "Piotr Zalewski", true, 1100L, "Trąbka 1", 1, 3),
                row(104L, "Kasia Wilk", true, 1102L, "Flet 1", 7, 9)));

        PartDeliveryResult result = service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        // Exactly ONE envelope, for Jan only.
        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.delivered()).hasSize(1);
        var janMail = result.delivered().get(0);
        assertThat(janMail.memberId()).isEqualTo(101L);
        assertThat(janMail.memberName()).isEqualTo("Jan Kowalski");
        assertThat(janMail.email()).isEqualTo("jan@test.com");
        assertThat(janMail.parts()).hasSize(2);

        var brass = janMail.parts().get(0);
        assertThat(brass.pieceTitle()).isEqualTo(PIECE_TITLE);
        assertThat(brass.role()).isEqualTo("Trąbka 1");
        assertThat(brass.pageFrom()).isEqualTo(1);
        assertThat(brass.pageTo()).isEqualTo(3);
        assertThat(brass.publicPartLink()).isEqualTo(BASE_URL + "/public/parts/" + tokenBrass);
        var drum = janMail.parts().get(1);
        assertThat(drum.role()).isEqualTo("Bęben");
        assertThat(drum.publicPartLink()).isEqualTo(BASE_URL + "/public/parts/" + tokenDrum);

        // The three other members land in their honest buckets — no silent drops.
        assertThat(result.skippedNoConsent()).containsExactly("Anna Nowak");
        assertThat(result.skippedNoEmail()).containsExactly("Piotr Zalewski");
        assertThat(result.noScoreFile()).hasSize(1);
        assertThat(result.noScoreFile().get(0)).contains("Kasia");
        assertThat(result.noScoreFile().get(0)).contains("Flet 1");
        assertThat(result.failedSend()).isEmpty();

        // Transport saw exactly one envelope, addressed to Jan; the renderer received both links.
        verify(emailSender, times(1)).sendHtmlEmail(eq("jan@test.com"), eq("Jan Kowalski"),
                subjectCaptor.capture(), anyString());
        verify(templateEngine, times(1)).process(eq("email/event-part"), contextCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains(EVENT_NAME);
        Context ctx = contextCaptor.getValue();
        List<PartDeliveryRow> ctxRows = ctxParts(ctx);
        assertThat(ctxRows).extracting(PartDeliveryRow::publicPartLink)
                .containsExactly(BASE_URL + "/public/parts/" + tokenBrass,
                        BASE_URL + "/public/parts/" + tokenDrum);
        assertThat(ctx.getVariable("bandName")).isEqualTo("Orkiestra Testowa");
        assertThat(ctx.getVariable("memberName")).isEqualTo("Jan Kowalski");

        // The covering-file gate refused Kasia's part — no token was ever minted for it.
        verify(tokenService, never()).tokenFor(eq(1102L), anyString());
        // And the consent gate stopped before Anna's member row was even read.
        verify(memberRepository, never()).findById(102L);
    }

    @Test
    void consentGatedMember_triggersNoSideEffectsAtAll() {
        // Anna has no consent — the gate must stop BEFORE any member lookup, open()/tokenFor()
        // call, or event lookup: a gated member must cost the system NOTHING.
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID))
                .thenReturn(distribution(row(102L, "Anna Nowak", false, 1101L, "Bęben", 4, 6)));

        PartDeliveryResult result = service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        assertThat(result.sent()).isZero();
        assertThat(result.skippedNoConsent()).containsExactly("Anna Nowak");
        assertThat(result.delivered()).isEmpty();
        verify(partLinkQueryService, never()).open(anyLong(), anyLong(), anyLong());
        verify(tokenService, never()).tokenFor(anyLong(), anyString());
        verifyNoInteractions(emailSender, eventRepository, templateEngine, bandQueryService);
    }

    @Test
    void emailContextCarriesEventAndMusicianDetails() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID))
                .thenReturn(distribution(row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3)));

        service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        verify(emailSender).sendHtmlEmail(eq("jan@test.com"), eq("Jan Kowalski"),
                subjectCaptor.capture(), anyString());
        verify(templateEngine).process(eq("email/event-part"), contextCaptor.capture());
        assertThat(subjectCaptor.getValue()).contains(EVENT_NAME);
        assertThat(subjectCaptor.getValue())
                .contains(event.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

        Context ctx = contextCaptor.getValue();
        assertThat(ctx.getVariable("eventName")).isEqualTo(EVENT_NAME);
        assertThat(ctx.getVariable("eventDate"))
                .isEqualTo(event.getDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        assertThat(ctx.getVariable("bandName")).isEqualTo("Orkiestra Testowa");
        assertThat(ctx.getVariable("memberName")).isEqualTo("Jan Kowalski");
        assertThat(ctxParts(ctx)).first()
                .satisfies(r -> {
                    assertThat(r.pieceTitle()).isEqualTo(PIECE_TITLE);
                    assertThat(r.pageFrom()).isEqualTo(1);
                    assertThat(r.pageTo()).isEqualTo(3);
                });
    }

    // ───────────────────────────── best-effort semantics ─────────────────────────────

    @Test
    void allEnvelopesFail_rethrowsSoTheUiCanSurfaceTheError() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID))
                .thenReturn(distribution(row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3)));
        doThrow(new RuntimeException("SMTP down")).when(emailSender)
                .sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nie udało się");
    }

    @Test
    void oneEnvelopeFails_othersStillDelivered() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        Member ewa = member(105L, "Ewa", "Wiśniewska", "ewa@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(memberRepository.findById(105L)).thenReturn(Optional.of(ewa));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(partLinkQueryService.open(1101L, PIECE_ID, BAND_ID)).thenReturn(link(1101L, "Bęben", 4, 6));
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        when(tokenService.tokenFor(1101L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID)).thenReturn(distribution(
                row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3),
                row(105L, "Ewa Wiśniewska", true, 1101L, "Bęben", 4, 6)));
        org.mockito.Mockito.doNothing()
                .when(emailSender).sendHtmlEmail(eq("jan@test.com"), anyString(), anyString(), anyString());
        doThrow(new RuntimeException("transient"))
                .when(emailSender).sendHtmlEmail(eq("ewa@test.com"), anyString(), anyString(), anyString());

        PartDeliveryResult result = service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.delivered()).extracting(PartDeliveryResult.Delivered::memberName)
                .containsExactly("Jan Kowalski");
        assertThat(result.failedSend()).containsExactly("Ewa Wiśniewska");
    }

    // ───────────────────────────── fail-closed (US-6.2 contract) ─────────────────────────────

    @Test
    void unknownBand_failsClosedBeforeAnyCollaboratorIsTouched() {
        doThrow(new IllegalArgumentException("Zespół 999 nie istnieje"))
                .when(partsQueryService).forEvent(EVENT_ID, 999L);

        assertThatThrownBy(() -> service.deliverParts(EVENT_ID, 999L, "admin@bandmanager.pl"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(partLinkQueryService, tokenService, emailSender,
                memberRepository, eventRepository, templateEngine, bandQueryService);
    }

    @Test
    void crossBandEvent_failsClosedBeforeAnyCollaboratorIsTouched() {
        doThrow(new IllegalStateException("Wydarzenie 55 nie należy do zespołu 7"))
                .when(partsQueryService).forEvent(EVENT_ID, BAND_ID);

        assertThatThrownBy(() -> service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(partLinkQueryService, tokenService, emailSender,
                memberRepository, eventRepository, templateEngine, bandQueryService);
    }

    @Test
    void emptyAssignments_noMailsNoErrorNoEventLookup() {
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID)).thenReturn(distribution());

        PartDeliveryResult result = service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        assertThat(result.sent()).isZero();
        assertThat(result.delivered()).isEmpty();
        assertThat(result.skippedNoConsent()).isEmpty();
        assertThat(result.failedSend()).isEmpty();
        verifyNoInteractions(emailSender, tokenService, partLinkQueryService,
                memberRepository, eventRepository, templateEngine, bandQueryService);
    }

    // ───────────────────────────── US-6.6 audit trail ─────────────────────────────

    @Test
    void everyPolicyDecision_writesAnAuditRowForExactlyTheRightParts() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        Member piotr = member(103L, "Piotr", "Zalewski", null);
        Member kasia = member(104L, "Kasia", "Wilk", "kasia@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(memberRepository.findById(103L)).thenReturn(Optional.of(piotr));
        when(memberRepository.findById(104L)).thenReturn(Optional.of(kasia));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        doThrow(new NoCoveringFileException("brak pliku dla stron 7–9"))
                .when(partLinkQueryService).open(1102L, PIECE_ID, BAND_ID);
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID)).thenReturn(distribution(
                row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3),      // → DELIVERED
                row(102L, "Anna Nowak", false, 1101L, "Bęben", 4, 6),          // → SKIPPED_NO_CONSENT
                row(103L, "Piotr Zalewski", true, 1100L, "Trąbka 1", 1, 3),    // → SKIPPED_NO_EMAIL
                row(104L, "Kasia Wilk", true, 1102L, "Flet 1", 7, 9)));        // → REFUSED_NO_SCORE_FILE

        service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        ArgumentCaptor<EventPartDelivery> rows = ArgumentCaptor.forClass(EventPartDelivery.class);
        verify(auditRepository, times(4)).save(rows.capture());
        var saved = rows.getAllValues();

        // One row per policy decision — outcomes, names and page ranges exactly as the UI sees them.
        assertThat(saved)
                .extracting(EventPartDelivery::getOutcome, EventPartDelivery::getDeliveredTo,
                        EventPartDelivery::getPartRole)
                .containsExactlyInAnyOrder(
                        tuple("DELIVERED", "Jan Kowalski", "Trąbka 1"),
                        tuple("SKIPPED_NO_CONSENT", "Anna Nowak", "Bęben"),
                        tuple("SKIPPED_NO_EMAIL", "Piotr Zalewski", "Trąbka 1"),
                        tuple("REFUSED_NO_SCORE_FILE", "Kasia Wilk", "Flet 1"));

        // Consent refusal is honest: no address by definition (the member was never resolved).
        EventPartDelivery consentRow = saved.stream()
                .filter(r -> "SKIPPED_NO_CONSENT".equals(r.getOutcome())).findFirst().orElseThrow();
        assertThat(consentRow.getRecipientEmail()).isNull();
        // The explicit refusals DO carry a reason the UI can display verbatim.
        EventPartDelivery refusedRow = saved.stream()
                .filter(r -> "REFUSED_NO_SCORE_FILE".equals(r.getOutcome())).findFirst().orElseThrow();
        assertThat(refusedRow.getReason()).contains("brak pliku");

        // Sender metadata — who, when, via which channel.
        saved.forEach(r -> {
            assertThat(r.getEventId()).isEqualTo(EVENT_ID);
            assertThat(r.getActor()).contains("admin@bandmanager.pl");
            assertThat(r.getSentAt()).isNotNull();
        });
    }

    @Test
    void sendFailure_auditsEveryPartOfTheFailedEnvelopeWithItsError() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(partLinkQueryService.open(1101L, PIECE_ID, BAND_ID)).thenReturn(link(1101L, "Bęben", 4, 6));
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        when(tokenService.tokenFor(1101L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        doThrow(new RuntimeException("SMTP down")).when(emailSender)
                .sendHtmlEmail(anyString(), anyString(), anyString(), anyString());
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID)).thenReturn(distribution(
                row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3),
                row(101L, "Jan Kowalski", true, 1101L, "Bęben", 4, 6)));

        // Every envelope failed → the service rethrows for the UI; the audit rows are written first.
        assertThatThrownBy(() -> service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nie udało się");

        ArgumentCaptor<EventPartDelivery> rows = ArgumentCaptor.forClass(EventPartDelivery.class);
        verify(auditRepository, times(2)).save(rows.capture());
        assertThat(rows.getAllValues())
                .allSatisfy(r -> {
                    assertThat(r.getOutcome()).isEqualTo("SEND_FAILED");
                    assertThat(r.getRecipientEmail()).isEqualTo("jan@test.com");
                    assertThat(r.getReason()).contains("SMTP down");
                })
                .extracting(EventPartDelivery::getPartRole)
                .containsExactlyInAnyOrder("Trąbka 1", "Bęben");
    }

    @Test
    void auditWriteFailure_neverBreaksTheDeliveryItself() {
        Member jan = member(101L, "Jan", "Kowalski", "jan@test.com");
        when(memberRepository.findById(101L)).thenReturn(Optional.of(jan));
        when(partLinkQueryService.open(1100L, PIECE_ID, BAND_ID)).thenReturn(link(1100L, "Trąbka 1", 1, 3));
        when(tokenService.tokenFor(1100L, "band:7")).thenReturn(UUID.randomUUID());
        stubEvent();
        stubBand();
        stubTemplate();
        doThrow(new RuntimeException("audit db down"))
                .when(auditRepository).save(any(EventPartDelivery.class));
        when(partsQueryService.forEvent(EVENT_ID, BAND_ID))
                .thenReturn(distribution(row(101L, "Jan Kowalski", true, 1100L, "Trąbka 1", 1, 3)));

        // The e-mail still goes out even though the history write is failing.
        PartDeliveryResult result = service.deliverParts(EVENT_ID, BAND_ID, "admin@bandmanager.pl");

        assertThat(result.sent()).isEqualTo(1);
        verify(emailSender).sendHtmlEmail(eq("jan@test.com"), anyString(), anyString(), anyString());
        verify(auditRepository).save(any(EventPartDelivery.class));
    }
}
