package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data adapter for the {@link CompositionInstrument} row (US-1.3).
 *
 * <p><b>Lazy-assoc warning:</b> consumers routinely call {@code ci.getComposition().getBand()}
 * (e.g. Parts tab band-ACL check before rendering) and {@code ci.getInstrument().getName()}
 * without a follow-up query — so every association-returning query below carries an explicit
 * {@code JOIN FETCH ci.composition} (and by extension composition.band is pre-warmed since
 * it was part of the same fetch graph). The same discipline as
 * {@link SpringDataCompositionRepository}.
 *
 * <p>The unique index on {@code (composition_id, lower(instrument_role))} mirrors the SQL
 * constraint in
 * {@code V36__create_composition_instrument.sql#uq_composition_instruments_role} — casefolded
 * collisions are rejected by the DB on a real Postgres. The H2 test profile does NOT apply
 * this index (it generates its own DDL from the entity model); see the NOTE at the top of
 * {@code CompositionInstrumentIT}.
 */
public interface SpringDataCompositionInstrumentRepository extends JpaRepository<CompositionInstrument, Long> {

    @Query("""
            SELECT ci FROM CompositionInstrument ci
            JOIN FETCH ci.composition JOIN FETCH ci.instrument
            WHERE ci.composition = :composition
            ORDER BY ci.pageFrom ASC, ci.id ASC""")
    List<CompositionInstrument> findAllByComposition(@Param("composition") Composition composition);

    @Query("""
            SELECT ci FROM CompositionInstrument ci
            JOIN FETCH ci.composition JOIN FETCH ci.instrument
            WHERE ci.composition = :composition
            ORDER BY ci.pageFrom ASC, ci.id ASC""")
    Page<CompositionInstrument> findAllByComposition(
            @Param("composition") Composition composition, Pageable pageable);

    @Query("""
            SELECT ci FROM CompositionInstrument ci
            JOIN FETCH ci.composition JOIN FETCH ci.instrument
            WHERE ci.composition = :composition AND LOWER(ci.instrumentRole) = LOWER(:role)""")
    Optional<CompositionInstrument> findByCompositionAndInstrumentRole(
            @Param("composition") Composition composition, @Param("role") String role);

    @Query("""
            SELECT ci FROM CompositionInstrument ci
            JOIN FETCH ci.composition JOIN FETCH ci.instrument
            WHERE ci.composition.id = :compositionId AND LOWER(ci.instrumentRole) = LOWER(:role)""")
    Optional<CompositionInstrument> findByCompositionIdAndInstrumentRole(
            @Param("compositionId") Long compositionId,
            @Param("role") String role);

    @Query("""
            SELECT ci FROM CompositionInstrument ci
            JOIN FETCH ci.composition JOIN FETCH ci.instrument
            WHERE ci.composition.band = :band
            ORDER BY ci.pageFrom ASC, ci.id ASC""")
    List<CompositionInstrument> findAllByBand(@Param("band") Band band);

    boolean existsByCompositionId(Long compositionId);

    @Modifying
    @Query("DELETE FROM CompositionInstrument ci WHERE ci.composition.id = :cid")
    int deleteAllByCompositionId(@Param("cid") Long compositionId);
}
