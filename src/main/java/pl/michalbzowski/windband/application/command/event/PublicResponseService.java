package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.PublicEventDetailDto;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicResponseService {

    private final EventInvitationRepository invitationRepository;
    private final EventRepository eventRepository;

    /**
     * Finds event details by invitation token for public view.
     */
    @Transactional(readOnly = true)
    public PublicEventDetailDto getEventByToken(String token) {
        EventInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token: " + token));

        BandEvent event = invitation.getBandEvent();
        Member member = invitation.getMember();

        EventParticipation participation = findParticipation(event, member);

        String instrumentName = participation.getInstrument() != null
                ? participation.getInstrument().getName()
                : member.getPrimaryInstrument().map(i -> i.getName()).orElse(null);

        String paymentType = event.getPaymentType().name();
        String paymentTypeDisplay = PublicEventDetailDto.formatPaymentType(paymentType);

        String response = participation.getResponse().name();
        boolean alreadyResponded = participation.getResponse() != ParticipationResponse.NO_RESPONSE
                && participation.getResponse() != null;

        return new PublicEventDetailDto(
                event.getId(),
                event.getName(),
                event.getDate(),
                event.getStartTime(),
                event.getLocation(),
                event.getEventType().name(),
                paymentType,
                paymentTypeDisplay,
                event.getPaymentAmount(),
                event.getPayoutPerMember(),
                event.getNotes(),
                member.getId(),
                member.getFirstName() + " " + member.getLastName(),
                member.getEmail(),
                instrumentName,
                alreadyResponded ? response : null,
                alreadyResponded,
                token
        );
    }

    /**
     * Records a response from a public magic link.
     * Uses BandEvent.recordResponse() which handles the domain logic.
     */
    public void recordResponse(String token, String responseValue) {
        EventInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invitation token: " + token));

        BandEvent event = invitation.getBandEvent();
        Member member = invitation.getMember();

        ParticipationResponse response = ParticipationResponse.valueOf(responseValue.toUpperCase());

        // Use the domain method which properly finds the participation and sets response
        event.recordResponse(member, response);
        eventRepository.save(event);

        // Mark invitation as responded
        invitation.markResponded();
        invitationRepository.save(invitation);
    }

    private EventParticipation findParticipation(BandEvent event, Member member) {
        return event.getParticipations().stream()
                .filter(p -> p.getMember().getId().equals(member.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Member " + member.getId() + " not invited to event " + event.getId()));
    }
}