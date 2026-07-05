package pl.michalbzowski.windband.domain.event;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventInvitationTest {

    @Test
    void shouldCreateInvitationWithDefaults() {
        // given
        Band band = Band.create("Test Band", "test-band");
        BandEvent event = BandEvent.create("Koncert", LocalDate.now(), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null);
        Member member = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band);

        // when
        EventInvitation invitation = EventInvitation.create(event, member);

        // then
        assertThat(invitation.getBandEvent()).isEqualTo(event);
        assertThat(invitation.getMember()).isEqualTo(member);
        assertThat(invitation.getToken()).isNotNull().isNotEmpty();
        assertThat(invitation.getToken()).hasSize(36); // UUID length
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.NOT_SENT);
        assertThat(invitation.getPreferredChannel()).isEqualTo("EMAIL");
        assertThat(invitation.getSentAt()).isNull();
        assertThat(invitation.getRespondedAt()).isNull();
    }

    @Test
    void shouldCreateInvitationWithCustomChannel() {
        // given
        Band band = Band.create("Test Band", "test-band");
        BandEvent event = BandEvent.create("Koncert", LocalDate.now(), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null);
        Member member = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band);

        // when
        EventInvitation invitation = EventInvitation.create(event, member, "MESSENGER");

        // then
        assertThat(invitation.getPreferredChannel()).isEqualTo("MESSENGER");
    }

    @Test
    void shouldMarkQueued() {
        // given
        EventInvitation invitation = createTestInvitation();

        // when
        invitation.markQueued();

        // then
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.QUEUED);
    }

    @Test
    void shouldMarkSent() {
        // given
        EventInvitation invitation = createTestInvitation();

        // when
        invitation.markSent();

        // then
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(invitation.getSentAt()).isNotNull();
    }

    @Test
    void shouldMarkFailed() {
        // given
        EventInvitation invitation = createTestInvitation();

        // when
        invitation.markFailed();

        // then
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void shouldMarkResponded() {
        // given
        EventInvitation invitation = createTestInvitation();

        // when
        invitation.markResponded();

        // then
        assertThat(invitation.getRespondedAt()).isNotNull();
    }

    @Test
    void shouldCheckStatusMethods() {
        // given
        EventInvitation notSent = createTestInvitation();
        EventInvitation sent = createTestInvitation();
        sent.markSent();
        EventInvitation failed = createTestInvitation();
        failed.markFailed();

        // then
        assertThat(notSent.isNotSent()).isTrue();
        assertThat(notSent.isSent()).isFalse();
        assertThat(notSent.isFailed()).isFalse();

        assertThat(sent.isSent()).isTrue();
        assertThat(sent.isNotSent()).isFalse();

        assertThat(failed.isFailed()).isTrue();
        assertThat(failed.isSent()).isFalse();
    }

    @Test
    void shouldGenerateUniqueTokens() {
        // given
        EventInvitation inv1 = createTestInvitation();
        EventInvitation inv2 = createTestInvitation();

        // then
        assertThat(inv1.getToken()).isNotEqualTo(inv2.getToken());
    }

    private EventInvitation createTestInvitation() {
        Band band = Band.create("Test Band", "test-band");
        BandEvent event = BandEvent.create("Koncert", LocalDate.now(), LocalTime.of(18, 0),
                "Rynek", EventType.CONCERT, band, PaymentType.FREE, null);
        Member member = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 1), band);
        return EventInvitation.create(event, member);
    }
}