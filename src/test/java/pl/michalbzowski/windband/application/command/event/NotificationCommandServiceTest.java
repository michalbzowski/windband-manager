package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationCommandServiceTest extends BaseIntegrationTest {

    @Autowired
    private NotificationCommandService notificationCommandService;

    @Autowired
    private EventCommandService eventCommandService;

    @Autowired
    private EventInvitationRepository invitationRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EventRepository eventRepository;

    private Band band;
    private Member member1;
    private Member member2;
    private Long eventId;

    @BeforeEach
    void setUp() {
        // Get the default band (teamId = 1 from seed data)
        band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
    }

    @Test
    void shouldCreateInvitationForMember() {
        // given
        member1 = memberRepository.findAllActiveByBandId(band.getId()).get(0);
        var event = createEvent();

        // when
        EventInvitation invitation = notificationCommandService.createInvitation(event.getId(), member1.getId());

        // then
        assertThat(invitation).isNotNull();
        assertThat(invitation.getToken()).isNotEmpty();
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.NOT_SENT);
        assertThat(invitation.getPreferredChannel()).isEqualTo("EMAIL");
    }

    @Test
    void shouldBeIdempotentOnDuplicateCreate() {
        // given
        member1 = memberRepository.findAllActiveByBandId(band.getId()).get(0);
        var event = createEvent();
        notificationCommandService.createInvitation(event.getId(), member1.getId());

        // when - creating again the same invitation
        EventInvitation invitation = notificationCommandService.createInvitation(event.getId(), member1.getId());

        // then - should return the existing one, not create a duplicate
        assertThat(invitation).isNotNull();
        var all = invitationRepository.findByEventId(event.getId());
        assertThat(all).hasSize(1);
    }

    @Test
    void shouldCreateInvitationsForAllEventMembers() {
        // given
        var activeMembers = memberRepository.findAllActiveByBandId(band.getId());
        member1 = activeMembers.get(0);
        member2 = activeMembers.size() > 1 ? activeMembers.get(1) : createMember("Anna", "Nowak");
        var event = createEvent();

        // Invite members to event
        InviteMemberCommand cmd1 = new InviteMemberCommand();
        cmd1.setEventId(event.getId());
        cmd1.setMemberId(member1.getId());
        eventCommandService.inviteMember(cmd1);

        InviteMemberCommand cmd2 = new InviteMemberCommand();
        cmd2.setEventId(event.getId());
        cmd2.setMemberId(member2.getId());
        eventCommandService.inviteMember(cmd2);

        // when - invitations should already be created by inviteMember
        var invitations = invitationRepository.findByEventId(event.getId());

        // then
        assertThat(invitations).hasSize(2);
        assertThat(invitations).allMatch(inv -> inv.getStatus() == NotificationStatus.NOT_SENT);
    }

    @Test
    void shouldQueueForSending() {
        // given
        member1 = memberRepository.findAllActiveByBandId(band.getId()).get(0);
        var event = createEvent();
        notificationCommandService.createInvitation(event.getId(), member1.getId());

        // when
        EventInvitation invitation = notificationCommandService.queueForSending(event.getId(), member1.getId());

        // then
        assertThat(invitation.getStatus()).isEqualTo(NotificationStatus.QUEUED);
    }

    @Test
    void shouldQueueAllPendingForEvent() {
        // given
        var activeMembers = memberRepository.findAllActiveByBandId(band.getId());
        member1 = activeMembers.get(0);
        member2 = activeMembers.size() > 1 ? activeMembers.get(1) : createMember("Anna", "Nowak");
        var event = createEvent();

        notificationCommandService.createInvitation(event.getId(), member1.getId());
        notificationCommandService.createInvitation(event.getId(), member2.getId());

        // when
        int queued = notificationCommandService.queueAllPending(event.getId());

        // then
        assertThat(queued).isEqualTo(2);
        var invitations = invitationRepository.findByEventId(event.getId());
        assertThat(invitations).allMatch(inv -> inv.getStatus() == NotificationStatus.QUEUED);
    }

    @Test
    void shouldGetInvitationStatus() {
        // given
        member1 = memberRepository.findAllActiveByBandId(band.getId()).get(0);
        var event = createEvent();

        // when - no invitation yet
        NotificationStatus notSent = notificationCommandService.getInvitationStatus(event.getId(), member1.getId());

        // then
        assertThat(notSent).isEqualTo(NotificationStatus.NOT_SENT);

        // when - after creating
        notificationCommandService.createInvitation(event.getId(), member1.getId());
        notificationCommandService.queueForSending(event.getId(), member1.getId());

        // then
        NotificationStatus queued = notificationCommandService.getInvitationStatus(event.getId(), member1.getId());
        assertThat(queued).isEqualTo(NotificationStatus.QUEUED);
    }

    @Test
    void shouldGetInvitationsForEvent() {
        // given
        var activeMembers = memberRepository.findAllActiveByBandId(band.getId());
        member1 = activeMembers.get(0);
        member2 = activeMembers.size() > 1 ? activeMembers.get(1) : createMember("Anna", "Nowak");
        var event = createEvent();

        // when
        notificationCommandService.createInvitation(event.getId(), member1.getId());
        notificationCommandService.createInvitation(event.getId(), member2.getId());

        // then
        var invitations = notificationCommandService.getInvitationsForEvent(event.getId());
        assertThat(invitations).hasSize(2);
    }

    @Test
    void shouldCreateInvitationsForEventMembersWhenCallingInviteMember() {
        // given
        var activeMembers = memberRepository.findAllActiveByBandId(band.getId());
        member1 = activeMembers.get(0);
        var event = createEvent();

        // when - invite member via EventCommandService
        InviteMemberCommand cmd = new InviteMemberCommand();
        cmd.setEventId(event.getId());
        cmd.setMemberId(member1.getId());
        eventCommandService.inviteMember(cmd);

        // then - EventInvitation should be automatically created
        var invitations = invitationRepository.findByEventId(event.getId());
        assertThat(invitations).hasSize(1);
        assertThat(invitations.get(0).getMember().getId()).isEqualTo(member1.getId());
        assertThat(invitations.get(0).getStatus()).isEqualTo(NotificationStatus.NOT_SENT);
    }

    private BandEvent createEvent() {
        CreateEventCommand cmd = new CreateEventCommand();
        cmd.setName("Test Event");
        cmd.setDate(LocalDate.now().plusDays(7));
        cmd.setStartTime(LocalTime.of(19, 0));
        cmd.setLocation("Test Location");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        return eventCommandService.createEvent(cmd, band.getId());
    }

    private Member createMember(String firstName, String lastName) {
        Member member = Member.create(firstName, lastName, LocalDate.of(1990, 1, 1), band);
        member.updateContact(firstName.toLowerCase() + "@test.com", null, false);
        return memberRepository.save(member);
    }
}