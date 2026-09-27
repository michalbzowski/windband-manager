package pl.michalbzowski.windband.application.command.composition;

import jakarta.validation.constraints.NotBlank;

/**
 * Form-bound command for the "dodaj mapowanie tag→rola" surface (US-7.3).
 */
public class AddInstrumentRoleMapCommand {

    @NotBlank(message = "Nazwa etykiety instrumentu jest wymagana")
    private String sourceTag;

    @NotBlank(message = "Cel — rola w utworze — jest wymagana")
    private String targetRolePattern;

    /** Optional admin-visible note; must be null or non-blank. */
    private String description;

    public AddInstrumentRoleMapCommand() {
    }

    public String getSourceTag() {
        return sourceTag;
    }

    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }

    public String getTargetRolePattern() {
        return targetRolePattern;
    }

    public void setTargetRolePattern(String targetRolePattern) {
        this.targetRolePattern = targetRolePattern;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
