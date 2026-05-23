package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.member.MemberNotFoundException;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class EventCommandService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final BandRepository bandRepository;

    public BandEvent createEvent(CreateEventCommand cmd) {
        Band band = bandRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Default band not found"));
        EventType type = EventType.valueOf(cmd.getEventType().toUpperCase());
        BandEvent event = BandEvent.create(
                cmd.getName(),
                cmd.getDate(),
                cmd.getStartTime(),
                cmd.getLocation(),
                type,
                band
        );
        if (cmd.getNotes() != null) {
            event.updateDetails(cmd.getName(), cmd.getDate(), cmd.getStartTime(), cmd.getLocation());
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

    public void deleteEvent(Long id) {
        BandEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        eventRepository.delete(event);
    }
}
