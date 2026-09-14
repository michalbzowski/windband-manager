package pl.michalbzowski.windband.application.command.composition;

import lombok.Data;

/**
 * Payload for updating the textual metadata of an existing composition.
 * All fields are nullable — a {@code null} value means "keep the current
 * stored value", so UI partial-forms preserve unaffected fields without
 * re-reading the entity. {@link #title}, when non-null, is re-validated by
 * the domain mutator against the same invariants as at creation time.
 */
@Data
public class UpdateCompositionCommand {

    private String title;
    private String description;
    private String composer;
    private String arranger;
}
