package pl.michalbzowski.windband.application.dto.composition;

import java.time.Instant;

/**
 * Immutable projection of ONE {@code composition_instruments} row as shown on the
 * US-3.03 Parts tab: every field a template could need, resolved by the service
 * while the owning transaction is still open — including {@code instrumentName},
 * which touches the lazy {@code instrument} association a template must never
 * dereference after the session closes ({@code LazyInitializationException}).
 */
public record CompositionInstrumentDto(
        Long id,
        Long compositionId,
        String instrumentRole,
        String instrumentName,
        Integer pageFrom,
        Integer pageTo,
        String fileRef,
        pl.michalbzowski.windband.domain.composition.PartSource source,
        Double confidenceScore,
        String verifiedBy,
        Instant verifiedAt) {
}
