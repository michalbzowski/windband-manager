package pl.michalbzowski.windband.application.command.event;

import lombok.Data;

@Data
public class InviteMemberCommand {
    private Long eventId;
    private Long memberId;
}
