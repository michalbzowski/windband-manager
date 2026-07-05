package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandService {

    private final EventInvitationRepository invitationRepository;
    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;

    /**
     * Creates an EventInvitation for a member invited to an event.
     * If an invitation already exists, returns the existing one (idempotent).
     */
    public EventInvitation createInvitation(Long eventId, Long memberId) {
        var existing = invitationRepository.findByEventIdAndMemberId(eventId, memberId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));

        EventInvitation invitation = EventInvitation.create(event, member);
        return invitationRepository.save(invitation);
    }

    /**
     * Creates EventInvitations for all members already invited to an event
     * who don't already have one. Idempotent — skips existing invitations.
     */
    public void createInvitationsForEvent(Long eventId) {
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        for (EventParticipation participation : event.getParticipations()) {
            var existing = invitationRepository.findByEventIdAndMemberId(eventId, participation.getMember().getId());
            if (existing.isEmpty()) {
                EventInvitation invitation = EventInvitation.create(event, participation.getMember());
                invitationRepository.save(invitation);
            }
        }
    }

    /**
     * Creates EventInvitations for a list of members.
     * Skips members who already have an invitation for this event.
     */
    public void createInvitationsForMembers(Long eventId, List<Member> members) {
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        for (Member member : members) {
            var existing = invitationRepository.findByEventIdAndMemberId(eventId, member.getId());
            if (existing.isEmpty()) {
                EventInvitation invitation = EventInvitation.create(event, member);
                invitationRepository.save(invitation);
            }
        }
    }

    /**
     * Marks an invitation as QUEUED for sending.
     * Creates the invitation first if it doesn't exist.
     */
    public EventInvitation queueForSending(Long eventId, Long memberId) {
        var invitations = invitationRepository.findByEventIdAndMemberId(eventId, memberId);
        EventInvitation invitation;
        if (invitations.isEmpty()) {
            BandEvent event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new EventNotFoundException(eventId));
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new MemberNotFoundException(memberId));
            invitation = EventInvitation.create(event, member);
        } else {
            invitation = invitations.get(0);
        }

        invitation.markQueued();
        return invitationRepository.save(invitation);
    }

    /**
     * Queues all NOT_SENT and FAILED invitations for an event for sending.
     * Returns the count of invitations queued.
     */
    public int queueAllPending(Long eventId) {
        var notSent = invitationRepository.findByEventIdAndStatus(eventId, NotificationStatus.NOT_SENT);
        var failed = invitationRepository.findByEventIdAndStatus(eventId, NotificationStatus.FAILED);

        int count = 0;
        for (EventInvitation invitation : notSent) {
            invitation.markQueued();
            invitationRepository.save(invitation);
            count++;
        }
        for (EventInvitation invitation : failed) {
            invitation.markQueued();
            invitationRepository.save(invitation);
            count++;
        }
        return count;
    }

    /**
     * Gets the invitation status for a member in an event.
     */
    public NotificationStatus getInvitationStatus(Long eventId, Long memberId) {
        var invitations = invitationRepository.findByEventIdAndMemberId(eventId, memberId);
        if (invitations.isEmpty()) {
            return NotificationStatus.NOT_SENT;
        }
        return invitations.get(0).getStatus();
    }

    /**
     * Gets all invitations for an event.
     */
    public List<EventInvitation> getInvitationsForEvent(Long eventId) {
        return invitationRepository.findByEventId(eventId);
    }
}