package pl.michalbzowski.windband.application.command.composition;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.PartShareToken;
import pl.michalbzowski.windband.domain.composition.PartShareTokenRepository;

/**
 * US-7.11 — lifecycle of the opaque share token attached to one voice row.
 *
 * <p>{@link #tokenFor} is <b>idempotent and lazy</b>: the first reader (the share modal or an
 * e-mail send) mints a UUIDv4 on demand, every later read returns the same stable credential.
 * This means legacy rows (V45 backfill absent in the H2 test profile, or a part added after
 * the migration ran) self-heal on first use instead of 404ing.</p>
 *
 * <p>{@link #rotate} replaces the live credential in place — the old link stops resolving
 * immediately (revoke). No row churn, unique part_id preserved.</p>
 */
@Service
public class PartShareTokenCommandService {

    private final PartShareTokenRepository tokenRepository;
    private final CompositionInstrumentRepository partRepository;

    public PartShareTokenCommandService(PartShareTokenRepository tokenRepository,
                                        CompositionInstrumentRepository partRepository) {
        this.tokenRepository = Objects.requireNonNull(tokenRepository);
        this.partRepository  = Objects.requireNonNull(partRepository);
    }

    @Transactional
    public UUID tokenFor(long partId, String actor) {
        CompositionInstrument part = partRepository.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Głos " + partId + " nie istnieje."));
        return tokenRepository.findByPartId(partId)
                .map(PartShareToken::getToken)
                .orElseGet(() -> tokenRepository.save(
                        PartShareToken.issue(part, actor, Instant.now())).getToken());
    }

    @Transactional
    public UUID rotate(long partId, String actor) {
        CompositionInstrument part = partRepository.findById(partId)
                .orElseThrow(() -> new IllegalArgumentException("Głos " + partId + " nie istnieje."));
        PartShareToken live = tokenRepository.findByPartId(partId)
                .orElseGet(() -> tokenRepository.save(
                        PartShareToken.issue(part, actor, Instant.now())));
        return tokenRepository.save(live.rotate(actor, Instant.now())).getToken();
    }
}
