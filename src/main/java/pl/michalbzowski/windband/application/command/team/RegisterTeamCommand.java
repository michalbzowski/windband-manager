package pl.michalbzowski.windband.application.command.team;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterTeamCommand {

    @NotBlank(message = "Team name is required")
    @Size(min = 2, max = 128)
    private String teamName;

    @NotBlank(message = "Team slug is required")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$",
             message = "Slug must be 3-64 chars, lowercase alphanumeric with hyphens")
    private String teamSlug;

    @NotBlank(message = "Admin username is required")
    @Size(min = 3, max = 64)
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$")
    private String adminUsername;

    @NotBlank(message = "Admin email is required")
    @Email
    private String adminEmail;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128)
    private String adminPassword;
}
