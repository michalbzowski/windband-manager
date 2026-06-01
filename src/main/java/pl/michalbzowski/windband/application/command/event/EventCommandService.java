package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Group;
import pl.michalbzowski.windband.domain.member.GroupRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final BandRepository bandRepository;

    public BandEvent createEvent(CreateEventCommand cmd) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        EventType type = EventType.valueOf(cmd.getEventType().toUpperCase());
        PaymentType paymentType = cmd.getPaymentType() != null
                ? PaymentType.valueOf(cmd.getPaymentType().toUpperCase())
                : PaymentType.FREE;
        BandEvent event = BandEvent.create(
                cmd.getName(),
                cmd.getDate(),
                cmd.getStartTime(),
                cmd.getLocation(),
                type,
                band,
                paymentType,
                cmd.getPaymentAmount()
        );
        if (cmd.getNotes() != null && !cmd.getNotes().isBlank()) {
            event.setNotes(cmd.getNotes());
        }
        return eventRepository.save(event);
    }

    public void inviteMember(InviteMemberCommand cmd) {
        BandEvent event = eventRepository.findById(cmd.getEventId())
                .orElseThrow(() -> new EventNotFoundException(cmd.getEventId()));
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        event.inviteMember(member);
        eventRepository.save(event);
    }

    public void recordResponse(RecordResponseCommand cmd) {
        BandEvent event = eventRepository.findById(cmd.getEventId())
                .orElseThrow(() -> new EventNotFoundException(cmd.getEventId()));
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        ParticipationResponse response = ParticipationResponse.valueOf(cmd.getResponse().toUpperCase());
        event.recordResponse(member, response);
        eventRepository.save(event);
    }

    public void recordPayment(RecordPaymentCommand cmd) {
        BandEvent event = eventRepository.findById(cmd.getEventId())
                .orElseThrow(() -> new EventNotFoundException(cmd.getEventId()));
        Member member = memberRepository.findById(cmd.getMemberId())
                .orElseThrow(() -> new MemberNotFoundException(cmd.getMemberId()));
        event.recordPayment(member, cmd.getAmount());
        eventRepository.save(event);
    }

    public void markPaymentPaid(Long eventId, Long memberId) {
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        event.markPaymentPaid(member);
        eventRepository.save(event);
    }

    public void updatePaymentStatus(Long eventId, Long memberId, String status) {
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        if (event.getPaymentType() != PaymentType.PAID_SPLIT && event.getPaymentType() != PaymentType.PAID_TO_TEAM) {
            throw new IllegalStateException("Payment status only applicable for PAID_SPLIT or PAID_TO_TEAM events");
        }
        if ("PAID".equals(status)) {
            event.markPaymentPaid(member);
        } else {
            // Reset to pending
            var participation = event.getParticipations().stream()
                    .filter(p -> p.getMember().getId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Member not found in event"));
            participation.recordPayment(event.getPayoutPerMember());
        }
        eventRepository.save(event);
    }

    public void deleteEvent(Long id) {
        BandEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        eventRepository.delete(event);
    }

    public void updateEvent(UpdateEventCommand cmd) {
        BandEvent event = eventRepository.findById(cmd.getId())
                .orElseThrow(() -> new EventNotFoundException(cmd.getId()));
        
        event.updateDetails(cmd.getName(), cmd.getDate(), cmd.getStartTime(), cmd.getLocation());
        
        // Update payment details - handle all cases including switching to/from FREE
        if (cmd.getPaymentType() != null) {
            PaymentType paymentType = PaymentType.valueOf(cmd.getPaymentType().toUpperCase());
            BigDecimal paymentAmount = cmd.getPaymentAmount();
            
            // If switching to FREE, clear payment amount
            if (paymentType == PaymentType.FREE) {
                paymentAmount = null;
            }
            
            event.updatePaymentDetails(paymentType, paymentAmount);
        }
        
        // Handle notes: set to notes value, or clear if empty/blank
        if (cmd.getNotes() != null) {
            if (cmd.getNotes().isBlank()) {
                event.setNotes(null);
            } else {
                event.setNotes(cmd.getNotes());
            }
        }
        
        eventRepository.save(event);
    }

    public void inviteGroup(InviteGroupCommand cmd) {
        BandEvent event = eventRepository.findById(cmd.getEventId())
                .orElseThrow(() -> new EventNotFoundException(cmd.getEventId()));
        Group group = groupRepository.findById(cmd.getGroupId())
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + cmd.getGroupId()));

        // Get already invited member IDs to avoid duplicates
        var invitedMemberIds = event.getParticipations().stream()
                .map(p -> p.getMember().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (var groupMember : group.getMembers()) {
            if (!invitedMemberIds.contains(groupMember.getMember().getId())) {
                event.inviteMember(groupMember.getMember());
            }
        }
        eventRepository.save(event);
    }
}
