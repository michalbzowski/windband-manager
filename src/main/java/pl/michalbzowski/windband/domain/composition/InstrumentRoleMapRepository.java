package pl.michalbzowski.windband.domain.composition;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository port for {@link InstrumentRoleMap} (US-1.4).
 *
 * <p>Convention (mirrors US-1.3's {@link CompositionInstrumentRepository}): the domain layer sees only
 * this interface; the infrastructure layer provides {@code SpringDataInstrumentRoleMapRepository} + a
 * thin {@code @Component} adapter. The entity itself never references a repository.
 *
 * <p><b>Band isolation:</b> every read path on this port is band-scoped and <em>case-insensitive on the
 * tag</em> — "Trąbka" / "trąbka" / "TRĄBKA" are the same logical tag, matching the unique index in V39
 * ({@code uq_instrument_role_map} on {@code (band_id, lower(source_tag), target_role_pattern)}). Callers
 * that reach this port from a different band's context get their own rows, never someone else's.
 *
 * <p><b>API surface:</b> every query is keyed by {@code Long bandId} — the domain port deliberately
 * exposes exactly one band accessor so callers cannot accidentally mix two look-shapes that do the
 * same work (the redundant {@code Band}-overload variant was dropped in review).
 */
public interface InstrumentRoleMapRepository {

    InstrumentRoleMap save(InstrumentRoleMap mapping);

    Optional<InstrumentRoleMap> findById(Long id);

    /** US-1.4: all roles a tag maps to for this band (US-1.5 distributor read path). Case-insensitive on tag. */
    List<InstrumentRoleMap> findByBandIdAndSourceTag(Long bandId, String sourceTag);

    /** US-1.4 spec: {@code findAllByBandId} — every mapping for a band (admin UI read path). */
    List<InstrumentRoleMap> findAllByBandId(Long bandId);

    /** Exact (band, tag-casefold, role) triple — used by the upsert / create-if-missing services. */
    Optional<InstrumentRoleMap> findByBandIdAndSourceTagAndTargetRolePattern(
            Long bandId, String sourceTag, String role);

    /**
     * Application-layer duplicate check used by the command service's pre-save guard.
     * Backed by a {@code SELECT EXISTS} plan — stops on the first matching row instead of scanning
     * the whole (band, tag) partition (cheap for small sets, still correct for large ones).
     */
    boolean existsByBandIdAndSourceTagAndTargetRolePattern(Long bandId, String sourceTag, String role);

    /** Cheap "any mappings for this band?" — used by admin UI to toggle the section. */
    boolean existsByBandId(Long bandId);

    void delete(InstrumentRoleMap mapping);

    /** Bulk remove all mappings for a band — used by tests / admin "reset defaults" action. */
    int deleteAllByBandId(Long bandId);
}
