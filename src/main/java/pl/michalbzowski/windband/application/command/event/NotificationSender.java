package pl.michalbzowski.windband.application.command.event;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.event.*;

@Service
@RequiredArgsConstructor
public class NotificationSender {

    private final EventInvitationRepository invitationRepository;
    private final ChannelResolver channelResolver;
    private final NotificationCommandService notificationCommandService;

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
        try {
            var event = invitation.getBandEvent();
            var member = invitation.getMember();

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