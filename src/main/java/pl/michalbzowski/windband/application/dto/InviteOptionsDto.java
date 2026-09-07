package pl.michalbzowski.windband.application.dto;

import java.util.List;

/**
 * Invite options for a single event: every group of the event's team with its
 * member id list (needed by the unified invite modal's dedup logic) plus every
 * active team member that is NOT yet a participant of this event.
 */
public record InviteOptionsDto(
        List<GroupOption> groups,
        List<MemberOption> members
) {
    public record GroupOption(
            Long id,
            String name,
            int memberCount,
            List<Long> memberIds
    ) {}

    public record MemberOption(
            Long id,
            String name
    ) {}
}
