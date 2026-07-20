package pl.michalbzowski.windband.application.command.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.dto.PublicEventDetailDto;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicResponseServiceTest extends BaseIntegrationTest {

    @Autowired
    private PublicResponseService publicResponseService;

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

    private Band band;
    private Member member;
    private Long eventId;
    private String token;

    @BeforeEach
    void setUp() {
        band = bandRepository.findById(1L).orElseThrow();
        member = memberRepository.findAllActiveByBandId(band.getId()).get(0);
        var event = createEvent();
        eventId = event.getId();

        // First invite member to the event (creates EventParticipation)
        InviteMemberCommand inviteCmd = new InviteMemberCommand();
        inviteCmd.setEventId(eventId);
        inviteCmd.setMemberId(member.getId());
        eventCommandService.inviteMember(inviteCmd);

        // This automatically creates EventInvitation via EventCommandService
        var invitations = invitationRepository.findByEventIdAndMemberId(eventId, member.getId());
        token = invitations.get(0).getToken();
    }

    @Test
    void shouldGetEventDetailsByToken() {
        // when
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);

        // then
        assertThat(detail).isNotNull();
        assertThat(detail.eventId()).isEqualTo(eventId);
        assertThat(detail.memberName()).contains(member.getFirstName());
        assertThat(detail.memberEmail()).isEqualTo(member.getEmail());
        assertThat(detail.eventName()).isEqualTo("Test Event");
        assertThat(detail.token()).isEqualTo(token);
        assertThat(detail.alreadyResponded()).isFalse();
    }

    @Test
    void shouldReturnInstrumentName() {
        // when
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);

        // then - the DTO exposes the invited member's primary instrument name
        assertThat(detail.instrumentName()).isNotNull();
    }

    @Test
    void shouldRecordConfirmedResponse() {
        // when
        publicResponseService.recordResponse(token, "CONFIRMED");

        // then
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);
        assertThat(detail.alreadyResponded()).isTrue();
        assertThat(detail.currentResponse()).isEqualTo("CONFIRMED");

        // Verify EventInvitation was marked as responded
        EventInvitation invitation = invitationRepository.findByToken(token).orElseThrow();
        assertThat(invitation.getRespondedAt()).isNotNull();
    }

    @Test
    void shouldRecordDeclinedResponse() {
        // when
        publicResponseService.recordResponse(token, "DECLINED");

        // then
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);
        assertThat(detail.alreadyResponded()).isTrue();
        assertThat(detail.currentResponse()).isEqualTo("DECLINED");
    }

    @Test
    void shouldRecordLaterResponse() {
        // when
        publicResponseService.recordResponse(token, "LATER");

        // then
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);
        assertThat(detail.alreadyResponded()).isTrue();
        assertThat(detail.currentResponse()).isEqualTo("LATER");
    }

    @Test
    void shouldAllowChangingResponse() {
        // given
        publicResponseService.recordResponse(token, "CONFIRMED");

        // when - change to DECLINED
        publicResponseService.recordResponse(token, "DECLINED");

        // then
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token);
        assertThat(detail.currentResponse()).isEqualTo("DECLINED");
    }

    @Test
    void shouldThrowOnInvalidToken() {
        assertThatThrownBy(() -> publicResponseService.getEventByToken("invalid-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid invitation token");
    }

    @Test
    void shouldRecordResponseThroughInviteAndPublicFlow() {
        // given - create another member and invite them
        Member member2 = createMember("Anna", "Nowak");

        InviteMemberCommand cmd = new InviteMemberCommand();
        cmd.setEventId(eventId);
        cmd.setMemberId(member2.getId());
        eventCommandService.inviteMember(cmd);

        // Get their token
        var invitations = invitationRepository.findByEventIdAndMemberId(eventId, member2.getId());
        String token2 = invitations.get(0).getToken();

        // when - respond via public link
        publicResponseService.recordResponse(token2, "CONFIRMED");

        // then
        PublicEventDetailDto detail = publicResponseService.getEventByToken(token2);
        assertThat(detail.alreadyResponded()).isTrue();
        assertThat(detail.currentResponse()).isEqualTo("CONFIRMED");
        assertThat(detail.memberName()).contains("Anna");
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
