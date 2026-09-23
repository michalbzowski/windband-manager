package pl.michalbzowski.windband.domain.composition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository port for {@link PartShareToken} (US-7.11).
 *
 * <p>The public voice link resolves through {@link #findByToken(UUID)} ONLY — callers
 * outside the owning team never learn a part id from the URL, and an unknown/garbage
 * token is indistinguishable from a revoked one (both are empty Optional → HTTP 404,
 * fail closed, no existence oracle).</p>
 */
public interface PartShareTokenRepository {

    PartShareToken save(PartShareToken token);

    Optional<PartShareToken> findByToken(UUID token);

    Optional<PartShareToken> findByPartId(Long partId);

    List<PartShareToken> findAllByPartId(Long partId);

    void delete(PartShareToken token);
}
