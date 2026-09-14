package pl.michalbzowski.windband.application.command.composition;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable DTO for creating a new composition. All fields are optional in the
 * wire sense (no bean-validation annotations) — the service applies the true
 * invariants:
 * <ul>
 *   <li>{@code title} is required, non-blank, and at most 200 characters</li>
 *   <li>{@code bandId} is provided by the caller as a separate argument (not a
 *       field) so that this command object stays a pure payload</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateCompositionCommand {

    private String title;
    private String description;
    private String composer;
    private String arranger;
}
