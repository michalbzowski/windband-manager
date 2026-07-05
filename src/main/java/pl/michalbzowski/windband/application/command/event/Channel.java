package pl.michalbzowski.windband.application.command.event;

import pl.michalbzowski.windband.domain.event.EventInvitation;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.member.Member;

/**
 * Abstraction for a communication channel used to send event invitations.
 * New channels (e.g. Messenger, Telegram) implement this interface.
 */
public interface Channel {

    /** Unique channel identifier (e.g. "EMAIL", "MESSENGER", "TELEGRAM") */
    String getName();

    /**
     * Sends an event invitation notification to a member.
     *
     * @param invitation the invitation record with token
     * @param event      the event details
     * @param member     the recipient member
     * @param baseUrl    application base URL for constructing magic links
     * @throws ChannelException if sending fails
     */
    void send(EventInvitation invitation, BandEvent event, Member member, String baseUrl);
}