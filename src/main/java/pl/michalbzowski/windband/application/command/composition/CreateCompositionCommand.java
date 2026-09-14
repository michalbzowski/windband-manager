package pl.michalbzowski.windband.application.command.composition;

import lombok.Data;

/**
 * Payload for creating a new composition. Field-level invariants are owned by
 * the domain factory ({@code Composition.create}) — this DTO is intentionally
 * a bare wire object so controllers stay free of business logic.
 */
@Data
public class CreateCompositionCommand {

    /** Required, non-blank, at most 200 characters. */
    private String title;

    /** Optional — human-readable description of the piece. */
    private String description;

    /** Optional — composer name. */
    private String composer;

    /** Optional — arranger / editor name. */
    private String arranger;
}
