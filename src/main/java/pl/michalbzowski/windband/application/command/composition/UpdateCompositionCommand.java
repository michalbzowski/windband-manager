package pl.michalbzowski.windband.application.command.composition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for updating the textual metadata of an existing composition.
 * {@code title} is required (non-blank) on update; the other fields are nullable —
 * a {@code null} value means \"keep the current stored value\", so UI
 * partial-forms preserve unaffected fields without re-reading the entity.
 */
@Data
public class UpdateCompositionCommand {

    /** Required, non-blank, at most 200 characters. */
    @NotBlank(message = "Tytuł jest wymagany")
    @Size(max = 200, message = "Tytuł może mieć maksymalnie 200 znaków")
    private String title;

    /** Optional — human-readable description of the piece. */
    @Size(max = 2000, message = "Opis może mieć maksymalnie 2000 znaków")
    private String description;

    /** Optional — composer name. */
    @Size(max = 150, message = "Kompozytor może mieć maksymalnie 150 znaków")
    private String composer;

    /** Optional — arranger / editor name. */
    @Size(max = 150, message = "Aranżer może mieć maksymalnie 150 znaków")
    private String arranger;
}
