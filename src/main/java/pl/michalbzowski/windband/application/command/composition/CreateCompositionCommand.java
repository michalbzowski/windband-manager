package pl.michalbzowski.windband.application.command.composition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Payload for creating a new composition. Field-level invariants are owned by
 * the domain factory ({@code Composition.create}) — this DTO is intentionally
 * a bare wire object so controllers stay free of business logic.
 */
@Data
public class CreateCompositionCommand {

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
