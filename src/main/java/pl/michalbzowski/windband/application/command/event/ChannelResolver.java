package pl.michalbzowski.windband.application.command.event;

import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the appropriate communication channel for a member.
 * Checks member preferences (email, preferred channel) and falls back to EMAIL.
 */
@Component
public class ChannelResolver {

    private final Map<String, Channel> channelMap;

    public ChannelResolver(List<Channel> channels) {
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(Channel::getName, Function.identity()));
    }

    /**
     * Resolves the channel for a member based on their info.
     * Currently always resolves to EMAIL if member has an email address.
     */
    public Channel resolveForMember(Member member) {
        if (member.getEmail() != null && !member.getEmail().isBlank()) {
            Channel emailChannel = channelMap.get("EMAIL");
            if (emailChannel != null) {
                return emailChannel;
            }
        }
        throw new IllegalStateException("No suitable channel found for member " + member.getId());
    }

    public Channel getChannel(String name) {
        Channel channel = channelMap.get(name);
        if (channel == null) {
            throw new IllegalArgumentException("Unknown channel: " + name);
        }
        return channel;
    }
}
