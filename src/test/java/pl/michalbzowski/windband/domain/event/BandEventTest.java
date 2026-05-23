package pl.michalbzowski.windband.domain.event;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRole;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class BandEventTest {

    @Test
    void shouldCreateEvent() {
        BandEvent event = BandEvent.create("Koncert Noworoczny",
                LocalDate.of(2025, 12, 31), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);

        assertThat(event.getName()).isEqualTo("Koncert Noworoczny");
        assertThat(event.getDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(event.getLocation()).isEqualTo("Rynek");
        assertThat(event.getEventType()).isEqualTo(EventType.CONCERT);
    }

    @Test
    void shouldInviteMember() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        event.inviteMember(member);

        assertThat(event.getNoResponseCount()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenInvitingSameMemberTwice() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        event.inviteMember(member);

        assertThatThrownBy(() -> event.inviteMember(member))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already invited");
    }

    @Test
    void shouldRecordResponse() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        event.inviteMember(member);
        event.recordResponse(member, ParticipationResponse.CONFIRMED);

        assertThat(event.getConfirmedCount()).isEqualTo(1);
        assertThat(event.getDeclinedCount()).isEqualTo(0);
    }

    @Test
    void shouldRecordPayment() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        event.inviteMember(member);
        event.recordPayment(member, new BigDecimal("200.00"));

        assertThat(event.getParticipations()).hasSize(1);
        assertThat(event.getParticipations().get(0).getPaymentAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(event.getParticipations().get(0).getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void shouldMarkPaymentPaid() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        event.inviteMember(member);
        event.recordPayment(member, new BigDecimal("200.00"));
        event.markPaymentPaid(member);

        assertThat(event.getParticipations().get(0).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void shouldCountResponsesCorrectly() {
        BandEvent event = BandEvent.create("Koncert",
                LocalDate.of(2025, 6, 15), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT);
        Member m1 = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);
        Member m2 = Member.create("Piotr", "Nowak", LocalDate.of(1985, 6, 20), MemberRole.MEMBER, false);
        Member m3 = Member.create("Anna", "Wiśniewska", LocalDate.of(1995, 3, 10), MemberRole.MEMBER, false);

        event.inviteMember(m1);
        event.inviteMember(m2);
        event.inviteMember(m3);

        event.recordResponse(m1, ParticipationResponse.CONFIRMED);
        event.recordResponse(m2, ParticipationResponse.DECLINED);

        assertThat(event.getConfirmedCount()).isEqualTo(1);
        assertThat(event.getDeclinedCount()).isEqualTo(1);
        assertThat(event.getNoResponseCount()).isEqualTo(1);
    }
}
