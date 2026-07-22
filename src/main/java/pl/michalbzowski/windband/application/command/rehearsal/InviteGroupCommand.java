package pl.michalbzowski.windband.application.command.rehearsal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Command for inviting every member of a group to a rehearsal. Mirrors
 * {@code pl.michalbzowski.windband.application.command.event.InviteGroupCommand}
 * — see the rationale on {@link InviteMemberCommand} for the package split.
 */
@Data
public class InviteGroupCommand {
    @NotNull
    private Long rehearsalId;

    @NotNull
    private Long groupId;
}
