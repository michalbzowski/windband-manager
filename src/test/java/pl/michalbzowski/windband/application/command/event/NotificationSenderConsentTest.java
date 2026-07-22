package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.EventInvitationRepository;
import pl.michalbzowski.windband.domain.event.NotificationStatus;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the privacy contract enforced by {@link NotificationSender}: members
 * who have NOT granted {@link ConsentType#EVENTS} consent are NEVER handed to
 * a {@link Channel} — the invitation is marked FAILED in place and the channel
 * is bypassed entirely.
 *
 * The companion UI test {@code EventConsentBadgeUiTest} verifies the same
 * behaviour through the rendered "Zgoda na informacje" column. The
 * companion integration test {@code EventConsentIntegrationTest} verifies
 * the full flow through the real Spring context.
 */
@ExtendWith(MockitoExtension.class)
class NotificationSenderConsentTest {

    @Mock EventInvitationRepository invitationRepository;
    @Mock ChannelResolver channelResolver;
    @Mock NotificationCommandService notificationCommandService;
    @Mock ConsentService consentService;
    @Mock Channel emailChannel;

    @InjectMocks NotificationSender sender;

    @Test
    void shouldSkipChannelAndMarkFailedWhenConsentDenied() {
        // given — member WITHOUT EVENTS consent
        Member member = newConsentingMember(1L, false);
        BandEvent event = newEvent();
        EventInvitation invitation = EventInvitation.create(event, member);
        when(consentService.isConsentGranted(member, ConsentType.EVENTS)).thenReturn(false);
        when(notificationCommandService.createInvitation(event.getId(), member.getId()))
                .thenReturn(invitation);

        // when
        sender.sendToMember(event.getId(), member.getId());

        // then — channel NEVER called, invitation marked FAILED
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(channelResolver, never()).resolveForMember(any());
        verify(emailChannel, never()).send(any(), any(), any(), any());
        verify(invitationRepository).save(invitation);
    }

    @Test
    void shouldCallChannelAndMarkSentWhenConsentGranted() {
        // given — member WITH EVENTS consent
        Member member = newConsentingMember(2L, true);
        BandEvent event = newEvent();
        EventInvitation invitation = EventInvitation.create(event, member);
        when(consentService.isConsentGranted(member, ConsentType.EVENTS)).thenReturn(true);
        when(channelResolver.resolveForMember(member)).thenReturn(emailChannel);
        when(notificationCommandService.createInvitation(event.getId(), member.getId()))
                .thenReturn(invitation);

        // when
        sender.sendToMember(event.getId(), member.getId());

        // then — channel IS called, invitation marked SENT
        verify(emailChannel, times(1))
                .send(eq(invitation), eq(event), eq(member), any());
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(invitationRepository).save(invitation);
    }

    @Test
    void shouldSkipChannelForEveryMemberWithoutConsentInSendToAll() {
        // given — 2 invitations: one with consent, one without
        Member consenting = newConsentingMember(10L, true);
        Member refusing = newConsentingMember(11L, false);
        BandEvent event = newEvent();
        EventInvitation invConsenting = EventInvitation.create(event, consenting);
        EventInvitation invRefusing = EventInvitation.create(event, refusing);
        when(invitationRepository.findByEventIdAndStatus(event.getId(), NotificationStatus.NOT_SENT))
                .thenReturn(List.of(invConsenting, invRefusing));
        when(invitationRepository.findByEventIdAndStatus(event.getId(), NotificationStatus.FAILED))
                .thenReturn(List.of());
        when(consentService.isConsentGranted(consenting, ConsentType.EVENTS)).thenReturn(true);
        when(consentService.isConsentGranted(refusing, ConsentType.EVENTS)).thenReturn(false);
        when(channelResolver.resolveForMember(consenting)).thenReturn(emailChannel);

        // when
        int sent = sender.sendToAll(event.getId());

        // then — only the consenting member went to the channel
        assertThat(sent).isEqualTo(1);
        assertThat(invConsenting.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(invRefusing.getStatus()).isEqualTo(NotificationStatus.FAILED);
        verify(emailChannel, times(1))
                .send(eq(invConsenting), any(), any(), any());
        verify(emailChannel, never())
                .send(eq(invRefusing), any(), any(), any());
    }

    @Test
    void shouldAlsoCheckConsentForSendToMember() {
        // given — single-member sendToMember, no consent
        Member member = newConsentingMember(20L, false);
        BandEvent event = newEvent();
        EventInvitation invitation = EventInvitation.create(event, member);
        when(consentService.isConsentGranted(member, ConsentType.EVENTS)).thenReturn(false);
        when(notificationCommandService.createInvitation(event.getId(), member.getId()))
                .thenReturn(invitation);

        // when
        sender.sendToMember(event.getId(), member.getId());

        // then
        verify(channelResolver, never()).resolveForMember(any());
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // --- helpers ---

    private static Member newConsentingMember(long id, boolean active) {
        // Member.create requires a non-null band (constructor asserts). We use a real
        // Band.create() so the factory accepts the call, but the band is never dereferenced
        // by NotificationSender.doSend() — consent is checked before any band touch.
        pl.michalbzowski.windband.domain.band.Band band =
                pl.michalbzowski.windband.domain.band.Band.create("Test Band", "test-band");
        Member m = Member.create("Test", "User " + id, LocalDate.of(1990, 1, 1), band);
        setField(m, "id", id);
        m.updateContact(id + "@test.com", null, active);
        return m;
    }

    private static BandEvent newEvent() {
        // BandEvent.create needs a non-null band. We only use it as a token; the channel
        // is mocked, so the band is never dereferenced.
        pl.michalbzowski.windband.domain.band.Band band =
                pl.michalbzowski.windband.domain.band.Band.create("Test Band", "test-band");
        BandEvent event = BandEvent.create("Test Event", LocalDate.now().plusDays(7),
                java.time.LocalTime.of(18, 0), "Test Location",
                pl.michalbzowski.windband.domain.event.EventType.CONCERT, band,
                pl.michalbzowski.windband.domain.event.PaymentType.FREE,
                java.math.BigDecimal.ZERO);
        return event;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
