package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.service.ConsentService;
import pl.michalbzowski.windband.domain.event.*;
import pl.michalbzowski.windband.domain.member.ConsentType;
import pl.michalbzowski.windband.domain.member.Member;

@Service
@RequiredArgsConstructor
public class NotificationSender {

    private final EventInvitationRepository invitationRepository;
    private final ChannelResolver channelResolver;
    private final NotificationCommandService notificationCommandService;
    private final ConsentService consentService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional
    public void sendToMember(Long eventId, Long memberId) {
        EventInvitation invitation = notificationCommandService.createInvitation(eventId, memberId);
        doSend(invitation);
    }

    @Transactional
    public int sendToAll(Long eventId) {
        var notSent = invitationRepository.findByEventIdAndStatus(eventId, NotificationStatus.NOT_SENT);
        var failed = invitationRepository.findByEventIdAndStatus(eventId, NotificationStatus.FAILED);

        int successCount = 0;
        for (EventInvitation invitation : notSent) {
            if (doSend(invitation)) successCount++;
        }
        for (EventInvitation invitation : failed) {
            if (doSend(invitation)) successCount++;
        }
        return successCount;
    }

    private boolean doSend(EventInvitation invitation) {
        Member member = invitation.getMember();

        // Honour the per-member consent BEFORE attempting to send — if a member has not
        // granted consent for event communications, the invitation is marked FAILED
        // without touching the channel. This is required by the privacy/comms contract
        // and by the EventConsentIntegrationTest regression.
        if (!consentService.isConsentGranted(member, ConsentType.EVENTS)) {
            invitation.markFailed();
            invitationRepository.save(invitation);
            System.out.println("[NotificationSender] Skipping invitation " + invitation.getId()
                    + " — member " + member.getId() + " has not granted EVENTS consent");
            return false;
        }

        try {
            var event = invitation.getBandEvent();

            Channel channel = channelResolver.resolveForMember(member);
            channel.send(invitation, event, member, baseUrl);

            invitation.markSent();
            invitationRepository.save(invitation);
            return true;
        } catch (Exception e) {
            invitation.markFailed();
            invitationRepository.save(invitation);
            System.err.println("[NotificationSender] Failed to send invitation " + invitation.getId()
                    + ": " + e.getMessage());
            return false;
        }
    }
}
