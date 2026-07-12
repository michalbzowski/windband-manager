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

class EventUpdateResendTest extends BaseIntegrationTest {

    @Autowired
    private EventCommandService eventCommandService;

    @Autowired
    private NotificationCommandService notificationCommandService;

    @Autowired
    private EventInvitationRepository invitationRepository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Band band;
    private Member member1;
    private Member member2;
    private Long eventId;

    @BeforeEach
    void setUp() {
        band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        var activeMembers = memberRepository.findAllActiveByBandId(band.getId());
        member1 = activeMembers.get(0);
        member2 = activeMembers.size() > 1 ? activeMembers.get(1) : createMember("Anna", "Nowak");
    }

    @Test
    void shouldResetSentInvitationsToNotSentAfterEventUpdate() {
        // given — create event, invite members, mark invitations as SENT
        var event = createEvent("Koncert sylwestrowy");
        inviteMember(event, member1);
        inviteMember(event, member2);

        var invitations = invitationRepository.findByEventId(event.getId());
        invitations.forEach(inv -> {
            inv.markSent();
            invitationRepository.save(inv);
        });

        // verify all were sent
        var beforeUpdate = invitationRepository.findByEventId(event.getId());
        assertThat(beforeUpdate)
                .extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.SENT);

        // when — update the event
        UpdateEventCommand cmd = new UpdateEventCommand();
        cmd.setId(event.getId());
        cmd.setName("Koncert sylwestrowy");
        cmd.setDate(LocalDate.now().plusDays(14));
        cmd.setStartTime(LocalTime.of(20, 0));
        cmd.setLocation("Nowa lokalizacja — Sala Kongresowa");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        eventCommandService.updateEvent(cmd);

        // then — all previously sent invitations should be reset to NOT_SENT
        var afterUpdate = invitationRepository.findByEventId(event.getId());
        assertThat(afterUpdate)
                .extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.NOT_SENT);
        assertThat(afterUpdate).hasSize(2);

        // and — sendToAll should find them and try to send (may fail in test env, but status should be QUEUED or FAILED)
        int reSent = notificationCommandService.queueAllPending(event.getId());
        assertThat(reSent).isEqualTo(2);
    }

    @Test
    void shouldNotResetInvitationsThatWereNotYetSent() {
        // given — create event, invite but do NOT send
        var event = createEvent("Próba generalna");
        inviteMember(event, member1);

        var before = invitationRepository.findByEventId(event.getId());
        assertThat(before).extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.NOT_SENT);

        // when — update event
        UpdateEventCommand cmd = new UpdateEventCommand();
        cmd.setId(event.getId());
        cmd.setName("Próba generalna (zmiana godziny)");
        cmd.setDate(LocalDate.now().plusDays(14));
        cmd.setStartTime(LocalTime.of(16, 0));
        cmd.setLocation("Sala prób");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        eventCommandService.updateEvent(cmd);

        // then — invitation should still be NOT_SENT (was never SENT, nothing to reset)
        var after = invitationRepository.findByEventId(event.getId());
        assertThat(after).extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.NOT_SENT);
        assertThat(after).hasSize(1);
    }

    @Test
    void shouldAllowMultipleUpdateResendCycles() {
        // given — create, invite, send
        var event = createEvent("Koncert plenerowy");
        inviteMember(event, member1);

        // mark as sent
        var inv = invitationRepository.findByEventId(event.getId()).get(0);
        inv.markSent();
        invitationRepository.save(inv);

        // when — first update + check reset
        updateEventName(event.getId(), "Koncert plenerowy (przeniesiony)");
        var afterFirst = invitationRepository.findByEventId(event.getId());
        assertThat(afterFirst).extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.NOT_SENT);

        // mark as sent again
        afterFirst.forEach(i -> { i.markSent(); invitationRepository.save(i); });

        // when — second update + check reset
        updateEventName(event.getId(), "Koncert plenerowy (odwołany, nowy termin)");
        var afterSecond = invitationRepository.findByEventId(event.getId());
        assertThat(afterSecond).extracting(EventInvitation::getStatus)
                .containsOnly(NotificationStatus.NOT_SENT);
    }

    @Test
    void shouldResetOnlySentNotFailed() {
        // given — create event with two members
        var event = createEvent("Wydarzenie testowe");
        inviteMember(event, member1);
        inviteMember(event, member2);

        // member1: SENT, member2: FAILED
        var invitations = invitationRepository.findByEventId(event.getId());
        var inv1 = invitations.stream().filter(i -> i.getMember().getId().equals(member1.getId())).findFirst().orElseThrow();
        var inv2 = invitations.stream().filter(i -> i.getMember().getId().equals(member2.getId())).findFirst().orElseThrow();
        inv1.markSent();
        inv2.markFailed();
        invitationRepository.save(inv1);
        invitationRepository.save(inv2);

        // when — update event
        updateEventName(event.getId(), "Wydarzenie testowe (update)");

        // then — SENT -> NOT_SENT, FAILED stays FAILED
        var after = invitationRepository.findByEventId(event.getId());
        var after1 = after.stream().filter(i -> i.getMember().getId().equals(member1.getId())).findFirst().orElseThrow();
        var after2 = after.stream().filter(i -> i.getMember().getId().equals(member2.getId())).findFirst().orElseThrow();

        assertThat(after1.getStatus()).isEqualTo(NotificationStatus.NOT_SENT);
        assertThat(after2.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    // --- helpers ---

    private BandEvent createEvent(String name) {
        CreateEventCommand cmd = new CreateEventCommand();
        cmd.setName(name);
        cmd.setDate(LocalDate.now().plusDays(7));
        cmd.setStartTime(LocalTime.of(19, 0));
        cmd.setLocation("Test Location");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        return eventCommandService.createEvent(cmd, band.getId());
    }

    private void inviteMember(BandEvent event, Member member) {
        InviteMemberCommand cmd = new InviteMemberCommand();
        cmd.setEventId(event.getId());
        cmd.setMemberId(member.getId());
        eventCommandService.inviteMember(cmd);
    }

    private void updateEventName(Long eventId, String newName) {
        UpdateEventCommand cmd = new UpdateEventCommand();
        cmd.setId(eventId);
        cmd.setName(newName);
        cmd.setDate(LocalDate.now().plusDays(14));
        cmd.setStartTime(LocalTime.of(19, 0));
        cmd.setLocation("Test Location");
        cmd.setEventType("CONCERT");
        cmd.setPaymentType("FREE");
        eventCommandService.updateEvent(cmd);
    }

    private Member createMember(String firstName, String lastName) {
        Member member = Member.create(firstName, lastName, LocalDate.of(1990, 1, 1), band);
        member.updateContact(firstName.toLowerCase() + "@test.com", null, false);
        return memberRepository.save(member);
    }
}