package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data implementation behind the domain port {@link InstrumentRoleMapRepository} (US-1.4).
 *
 * <p><b>Casefolding contract:</b> every tag-scoped query compares {@code LOWER(m.sourceTag)} to
 * {@code LOWER(:sourceTag)}. This mirrors the V39 unique index
 * ({@code uq_instrument_role_map} on {@code (band_id, lower(source_tag), target_role_pattern)})
 * so an app-level duplicate check and the DB-level net never disagree: "Trąbka", "TRĄBKA" and
 * "trąbka" are the same logical tag for both the schema and these queries. The derived-method
 * alternative ({@code findByBandIdAndSourceTag}) would be byte-exact / case-sensitive and would
 * silently bypass that contract — it is therefore deliberately NOT declared here.
 *
 * <p>{@code m.band} is fetched eagerly inside each query's fetch graph ({@code JOIN FETCH m.band})
 * so callers can read {@code m.getBand().getName()} without an extra round-trip, matching the
 * adapter discipline used by {@link SpringDataCompositionInstrumentRepository}.
 *
 * <p><b>API surface:</b> every method is keyed by {@code Long bandId} — the redundant
 * {@code Band}-entity overload present in the first cut of the PR was dropped so callers have a
 * single, unambiguous lookup shape (and because JPA does not need the entity here: the unique index
 * is on the scalar {@code band_id}).
 */
public interface SpringDataInstrumentRoleMapRepository extends JpaRepository<InstrumentRoleMap, Long> {

    @Query("""
            SELECT m FROM InstrumentRoleMap m
            JOIN FETCH m.band
            WHERE m.band.id = :bandId AND LOWER(m.sourceTag) = LOWER(:sourceTag)
            ORDER BY m.targetRolePattern ASC, m.id ASC""")
    List<InstrumentRoleMap> findByBandIdAndSourceTag(@Param("bandId") Long bandId, @Param("sourceTag") String sourceTag);

    @Query("""
            SELECT m FROM InstrumentRoleMap m
            JOIN FETCH m.band
            WHERE m.band.id = :bandId
            ORDER BY LOWER(m.sourceTag) ASC, m.targetRolePattern ASC, m.id ASC""")
    List<InstrumentRoleMap> findAllByBandId(@Param("bandId") Long bandId);

    @Query("""
            SELECT m FROM InstrumentRoleMap m
            JOIN FETCH m.band
            WHERE m.band.id = :bandId AND LOWER(m.sourceTag) = LOWER(:sourceTag) AND m.targetRolePattern = :role""")
    Optional<InstrumentRoleMap> findByBandIdAndSourceTagAndTargetRolePattern(
            @Param("bandId") Long bandId, @Param("sourceTag") String sourceTag, @Param("role") String role);

    /**
     * Cheap existence probe — compiled to {@code SELECT EXISTS (...)} instead of {@code SELECT COUNT}.
     * Both are correct, but {@code EXISTS} stops at the first matching row in either direction and
     * makes the intent ("is there a row?") obvious at the call site. Spring Data returns this as
     * a boolean without scanning the rest of the (band, tag) partition.
     */
    @Query("""
            SELECT CASE WHEN EXISTS (
                SELECT m FROM InstrumentRoleMap m
                WHERE m.band.id = :bandId AND LOWER(m.sourceTag) = LOWER(:sourceTag)
                  AND m.targetRolePattern = :role
            ) THEN TRUE ELSE FALSE END""")
    boolean existsByBandIdAndSourceTagAndTargetRolePattern(
            @Param("bandId") Long bandId, @Param("sourceTag") String sourceTag, @Param("role") String role);

    boolean existsByBandId(Long bandId);

    @Modifying
    @Query("DELETE FROM InstrumentRoleMap m WHERE m.band.id = :bandId")
    int deleteAllByBandId(@Param("bandId") Long bandId);
}
