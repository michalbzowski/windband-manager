package pl.michalbzowski.windband.application.command.composition;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload for adding one {@code CompositionInstrument} part mapping to a composition.
 *
 * <p>Validation contract (checked via Bean Validation in the controller and/or the
 * command-side fallback in {@code CompositionCommandService.addPart}):</p>
 * <ul>
 *   <li>{@code instrumentId} — non-null, must belong to the same band as the composition.</li>
 *   <li>{@code role} — non-blank; trimmed and stored lowercase-normalized by the service.</li>
 *   <li>{@code pageFrom} / {@code pageTo} — integers in [1, 999]; {@code pageFrom <= pageTo}
 *       is enforced at the domain factory layer ({@code CompositionInstrument.forComposition}).</li>
 * </ul>
 *
 * <p>The {@code confidence} field is intentionally omitted from this form payload: the US-7.1
 * manual-entry flow always records a fully-trusted mapping, so the service passes {@code 1.0}
 * (its default when no confidence argument is supplied).</p>
 */
@Data
public class AddPartCommand {

    /** Must exist and belong to the same band as the target composition. */
    @NotNull(message = "Wybierz instrument")
    private Long instrumentId;

    /** Human-readable role within the piece (e.g. "Partytura", "Flet 1"). */
    @NotBlank(message = "Rola jest wymagana")
    private String role;

    /** Inclusive lower bound of the mapped page range. */
    @Min(value = 1, message = "Strona od musi być >= 1")
    @Max(value = 999, message = "Strona od musi być <= 999")
    private Integer pageFrom = 1;

    /** Inclusive upper bound of the mapped page range. */
    @Min(value = 1, message = "Strona do musi być >= 1")
    @Max(value = 999, message = "Strona do musi być <= 999")
    private Integer pageTo = 2;
}
