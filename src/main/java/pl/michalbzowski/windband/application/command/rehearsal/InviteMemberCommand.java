package pl.michalbzowski.windband.application.command.rehearsal;

import lombok.Data;

/**
 * Command for inviting a single member to a rehearsal. Mirrors
 * {@code pl.michalbzowski.windband.application.command.event.InviteMemberCommand}
 * but lives in the rehearsal package to keep the bounded contexts
 * separate (rehearsals use {@code Attendance} rows instead of
 * {@code EventParticipation} rows; the invite flow must not share a class
 * with the event module).
 */
@Data
public class InviteMemberCommand {
    private Long rehearsalId;
    private Long memberId;
}
