package pl.michalbzowski.windband.application.command.event;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteGroupCommand {
    @NotNull
    private Long eventId;

    @NotNull
    private Long groupId;
}
