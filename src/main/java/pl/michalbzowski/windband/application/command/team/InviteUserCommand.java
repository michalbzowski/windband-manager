package pl.michalbzowski.windband.application.command.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import pl.michalbzowski.windband.domain.user.TeamRole;

@Data
public class InviteUserCommand {

    @NotBlank
    @Email
    private String email;

    @NotNull
    private TeamRole role = TeamRole.MEMBER;
}
