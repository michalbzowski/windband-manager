package pl.michalbzowski.windband.application.query.attention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventParticipation;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentStatus;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.event.ParticipationResponse;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.rehearsal.Attendance;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttentionItemCollector.
 * Tests pluggable attention conditions logic.
 */
@ExtendWith(MockitoExtension.class)
class AttentionItemCollectorTest {

    @Mock
    private EventQueryService eventQueryService;

    @Mock
    private RehearsalQueryService rehearsalQueryService;

    @InjectMocks
    private AttentionItemCollector collector;

    private Band band;

    @BeforeEach
    void setUp() {
        band = Band.create("Test Band", "test-band");
    }

    @Test
    void collect_returns_empty_list_when_no_past_events() {
        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_ignores_future_events() {
        BandEvent futureEvent = BandEvent.create(
                "Future Concert",
                LocalDate.now().plusDays(7),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(futureEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_ignores_free_events() {
        BandEvent freeEvent = BandEvent.create(
                "Free Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.FREE,
                BigDecimal.ZERO
        );

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(freeEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_ignores_paid_split_events_with_no_unpaid_confirmations() {
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );

        // Add confirmed participation with COMPLETED payment
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.CONFIRMED);
        markPaymentPaid(participation);

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_returns_attention_item_for_past_paid_split_with_confirmed_but_payment_not_recorded() {
        // Owner requirement: an event qualifies for attention as long as ONE
        // confirmed participant does NOT have paymentStatus == PAID. The
        // payment status can be PENDING (recordPayment called) OR
        // NOT_APPLICABLE (recordPayment never called — the admin has yet
        // to register a payment for this confirmed person at all).
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(2),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );
        setEventId(paidEvent, 77L);

        // Invite + CONFIRM but DO NOT call recordPayment.
        // The participation's paymentStatus stays at the default NOT_APPLICABLE.
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.CONFIRMED);
        // Sanity: paymentStatus is still NOT_APPLICABLE here.
        assertThat(participation.getPaymentStatus())
                .as("paymentStatus should remain NOT_APPLICABLE since recordPayment was never called")
                .isEqualTo(PaymentStatus.NOT_APPLICABLE);

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result)
                .as("any confirmed member with paymentStatus != PAID should trigger attention")
                .hasSize(1);
        UpcomingItemDto item = result.get(0);
        assertThat(item.kind()).isEqualTo("ATTENTION_PAYMENT");
        assertThat(item.id()).isEqualTo(77L);
        assertThat(item.title()).isEqualTo("Paid Concert");
    }

    @Test
    void collect_returns_attention_item_when_one_of_three_confirmed_is_unpaid() {
        // Owner requirement: even ONE unpaid confirmed person is enough.
        // 3 confirmed members, 2 marked as PAID, 1 still PENDING → attention.
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(3),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(300)
        );
        setEventId(paidEvent, 88L);

        Member alice = Member.create("Alice", "A", LocalDate.of(2000, 1, 1), band);
        Member bob = Member.create("Bob", "B", LocalDate.of(2000, 1, 1), band);
        Member carol = Member.create("Carol", "C", LocalDate.of(2000, 1, 1), band);
        setMemberId(alice, 1L);
        setMemberId(bob, 2L);
        setMemberId(carol, 3L);

        paidEvent.inviteMember(alice);
        paidEvent.inviteMember(bob);
        paidEvent.inviteMember(carol);

        // Alice & Bob: CONFIRMED + PAID (fully settled)
        var alicePart = findParticipation(paidEvent, alice);
        setParticipationResponse(alicePart, ParticipationResponse.CONFIRMED);
        markPaymentPaid(alicePart);

        var bobPart = findParticipation(paidEvent, bob);
        setParticipationResponse(bobPart, ParticipationResponse.CONFIRMED);
        markPaymentPaid(bobPart);

        // Carol: CONFIRMED + PENDING (only one unpaid is enough)
        var carolPart = findParticipation(paidEvent, carol);
        setParticipationResponse(carolPart, ParticipationResponse.CONFIRMED);
        recordPayment(carolPart, BigDecimal.valueOf(100));

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result)
                .as("a single unpaid confirmed person is enough to trigger attention")
                .hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(88L);
    }

    @Test
    void collect_ignores_only_declined_participations() {
        // All invited members DECLINED → no one is paying → no attention needed.
        // (Past PAID_SPLIT events where everyone said "no" don't have an
        // outstanding payment to chase — the payout is moot.)
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );
        setEventId(paidEvent, 99L);

        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.DECLINED);
        recordPayment(participation, BigDecimal.valueOf(100)); // sets status to PENDING

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result)
                .as("declined members don't owe money — no attention needed")
                .isEmpty();
    }

    @Test
    void collect_returns_attention_item_for_past_paid_split_with_unpaid_confirmed() {
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );
        setEventId(paidEvent, 99L);

        // Add confirmed participation with PENDING payment
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.CONFIRMED);
        recordPayment(participation, BigDecimal.valueOf(100)); // sets paymentStatus to PENDING

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).hasSize(1);
        UpcomingItemDto item = result.get(0);
        assertThat(item.kind()).isEqualTo("ATTENTION_PAYMENT");
        assertThat(item.id()).isEqualTo(99L);
        assertThat(item.title()).isEqualTo("Paid Concert");
        assertThat(item.subtitle()).isEqualTo("Wypłata nie została rozdysponowana");
        assertThat(item.href()).isEqualTo("/events/99");
        assertThat(item.icon()).isEqualTo("🚨");
    }

    @Test
    void collect_ignores_declined_participations_with_unpaid_status() {
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );
        setEventId(paidEvent, 99L);

        // Add DECLINED participation with PENDING payment (should not trigger attention)
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.DECLINED);
        recordPayment(participation, BigDecimal.valueOf(100)); // sets paymentStatus to PENDING

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(new ArrayList<>());

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    // ===== Rehearsal condition tests =====

    @Test
    void collect_ignores_future_rehearsals() {
        Rehearsal futureRehearsal = createRehearsal(LocalDate.now().plusDays(7), LocalTime.of(18, 0), "Venue");

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(futureRehearsal));

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_returns_attention_item_for_past_rehearsal_with_no_response_attendance() {
        Rehearsal pastRehearsal = createRehearsal(LocalDate.now().minusDays(2), LocalTime.of(19, 0), "Venue");
        setRehearsalId(pastRehearsal, 42L);

        // Add member with NO_RESPONSE attendance
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        pastRehearsal.inviteMember(member);
        var attendance = findAttendance(pastRehearsal, member);
        // Default status is NO_RESPONSE, so no need to change it

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(pastRehearsal));

        var result = collector.collect(1L);

        assertThat(result).hasSize(1);
        UpcomingItemDto item = result.get(0);
        assertThat(item.kind()).isEqualTo("ATTENTION_REHEARSAL_ATTENDANCE");
        assertThat(item.id()).isEqualTo(42L);
        assertThat(item.title()).isEqualTo("Próba");
        assertThat(item.subtitle()).isEqualTo("Niepodejmowana decyzja o obecności (brak odpowiedzi)");
        assertThat(item.href()).isEqualTo("/rehearsals/42");
        assertThat(item.icon()).isEqualTo("🎵");
    }

    @Test
    void collect_ignores_rehearsal_with_all_attendance_confirmed() {
        Rehearsal pastRehearsal = createRehearsal(LocalDate.now().minusDays(1), LocalTime.of(18, 0), "Venue");

        // Add member with PRESENT attendance
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        pastRehearsal.inviteMember(member);
        var attendance = findAttendance(pastRehearsal, member);
        setAttendanceStatus(attendance, AttendanceStatus.PRESENT);

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(pastRehearsal));

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_returns_attention_item_for_past_rehearsal_without_location() {
        Rehearsal pastRehearsal = createRehearsal(LocalDate.now().minusDays(3), LocalTime.of(18, 0), null);
        setRehearsalId(pastRehearsal, 55L);

        // No attendances needed for this condition
        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(pastRehearsal));

        var result = collector.collect(1L);

        assertThat(result).hasSize(1);
        UpcomingItemDto item = result.get(0);
        assertThat(item.kind()).isEqualTo("ATTENTION_REHEARSAL_LOCATION");
        assertThat(item.id()).isEqualTo(55L);
        assertThat(item.title()).isEqualTo("Próba");
        assertThat(item.subtitle()).isEqualTo("Brak potwierdzonej lokalizacji — członkowie nie wiedzą gdzie się zebrać");
        assertThat(item.href()).isEqualTo("/rehearsals/55");
        assertThat(item.icon()).isEqualTo("📍");
        assertThat(item.badge()).isEqualTo("Brak info");
    }

    @Test
    void collect_ignores_rehearsal_with_location() {
        Rehearsal pastRehearsal = createRehearsal(LocalDate.now().minusDays(1), LocalTime.of(18, 0), "Music Room");

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(new ArrayList<>());
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(pastRehearsal));

        var result = collector.collect(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void collect_returns_both_event_and_rehearsal_attention_items() {
        // Event with unpaid payment
        BandEvent paidEvent = BandEvent.create(
                "Paid Concert",
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 0),
                "Concert Hall",
                EventType.CONCERT,
                band,
                PaymentType.PAID_SPLIT,
                BigDecimal.valueOf(100)
        );
        setEventId(paidEvent, 99L);
        Member member = Member.create("John", "Doe", LocalDate.of(2000, 1, 1), band);
        setMemberId(member, 1L);
        paidEvent.inviteMember(member);
        var participation = findParticipation(paidEvent, member);
        setParticipationResponse(participation, ParticipationResponse.CONFIRMED);
        recordPayment(participation, BigDecimal.valueOf(100));

        // Rehearsal with NO_RESPONSE attendance
        Rehearsal pastRehearsal = createRehearsal(LocalDate.now().minusDays(2), LocalTime.of(19, 0), "Venue");
        setRehearsalId(pastRehearsal, 42L);
        pastRehearsal.inviteMember(member);
        var attendance = findAttendance(pastRehearsal, member);
        // Default is NO_RESPONSE

        when(eventQueryService.getPastEvents(anyLong())).thenReturn(List.of(paidEvent));
        when(rehearsalQueryService.getPastRehearsals(anyLong())).thenReturn(List.of(pastRehearsal));

        var result = collector.collect(1L);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(UpcomingItemDto::kind)).containsExactlyInAnyOrder(
                "ATTENTION_PAYMENT",
                "ATTENTION_REHEARSAL_ATTENDANCE"
        );
    }

    // Reflection helpers for setting IDs (since domain entities use @Id without setters)
    private static void setEventId(BandEvent event, Long id) {
        try {
            var f = BandEvent.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(event, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setMemberId(Member member, Long id) {
        try {
            var f = Member.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(member, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setRehearsalId(Rehearsal rehearsal, Long id) {
        try {
            var f = Rehearsal.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(rehearsal, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Rehearsal createRehearsal(LocalDate date, LocalTime startTime, String location) {
        try {
            var constructor = Rehearsal.class.getDeclaredConstructor(LocalDate.class, LocalTime.class, String.class, Band.class);
            constructor.setAccessible(true);
            return constructor.newInstance(date, startTime, location, band);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static EventParticipation findParticipation(BandEvent event, Member member) {
        return event.getParticipations().stream()
                .filter(p -> p.getMember().equals(member))
                .findFirst()
                .orElseThrow();
    }

    private static Attendance findAttendance(Rehearsal rehearsal, Member member) {
        return rehearsal.getAttendances().stream()
                .filter(a -> a.getMember().equals(member))
                .findFirst()
                .orElseThrow();
    }

    // Reflection helpers for package-private methods
    private static void setParticipationResponse(EventParticipation participation, ParticipationResponse response) {
        try {
            Method method = EventParticipation.class.getDeclaredMethod("setResponse", ParticipationResponse.class);
            method.setAccessible(true);
            method.invoke(participation, response);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void markPaymentPaid(EventParticipation participation) {
        try {
            Method method = EventParticipation.class.getDeclaredMethod("markPaymentPaid");
            method.setAccessible(true);
            method.invoke(participation);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void recordPayment(EventParticipation participation, BigDecimal amount) {
        try {
            Method method = EventParticipation.class.getDeclaredMethod("recordPayment", BigDecimal.class);
            method.setAccessible(true);
            method.invoke(participation, amount);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setAttendanceStatus(Attendance attendance, AttendanceStatus status) {
        try {
            Method method = Attendance.class.getDeclaredMethod("updateStatus", AttendanceStatus.class);
            method.setAccessible(true);
            method.invoke(attendance, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
