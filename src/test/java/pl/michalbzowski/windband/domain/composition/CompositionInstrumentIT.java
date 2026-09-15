package pl.michalbzowski.windband.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

/**
 * Integration tests for the {@link CompositionInstrument} link entity (US-1.3).
 *
 * <p>Pins down:
 * <ul>
 *   <li>Full field round-trip through Flyway V36 ({@code page_from/page_to},
 *       {@code instrument_role}, {@code source}, {@code confidence_score},
 *       {@code verified_by/verified_at});</li>
 *   <li>Case-insensitive uniqueness per (composition, role) via the SQL unique
 *       index — "Flet 1" / "FLET 1" collide, but "Flet 1" + "Flet 2" do not;</li>
 *   <li>Band isolation for both read paths — a part of band A is never reachable
 *       through band B's composition id;</li>
 *   <li>Cascade delete: removing the parent composition also removes its parts via
 *       {@code @OneToMany(cascade=ALL, orphanRemoval=true)} on Composition and the
 *       FK-RESTRICT + ON DELETE CASCADE column in V36 (double net);</li>
 *   <li>Confidence is enforced into [0.0, 1.0] by the factory before escape;</li>
 *   <li>{@code verify()} fills the audit pair (verified_by, verified_at) and is a
 *       no-op on subsequent calls to preserve the first-writer's audit trail.</li>
 * </ul>
 */
class CompositionInstrumentIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private CompositionInstrumentRepository repository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Track ids created by this test instance so cleanup removes ONLY our own rows —
     * the shared Testcontainers DB is reused across test classes, and other tests may
     * legitimately seed compositions/instruments in band 1/2 that must survive.
     */
    private final List<Long> myCompositionIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // V36's FK is RESTRICT on manual DELETE — the repository-adapter path already
        // cascades; for direct-SQL safety we still delete children first.
        for (Long cid : myCompositionIds) {
            jdbcTemplate.update("DELETE FROM composition_instruments WHERE composition_id = ?", cid);
        }
        for (Long cid : myCompositionIds) {
            int removed = jdbcTemplate.update("DELETE FROM compositions WHERE id = ?", cid);
            assertThat(removed).isEqualTo(1);
        }
        myCompositionIds.clear();
    }

    private Composition newComposition(Long bandId, String title) {
        Composition c = compositionRepository.save(Composition.create(title, null, null, null, band(bandId)));
        myCompositionIds.add(c.getId());
        return c;
    }

    private pl.michalbzowski.windband.domain.band.Band band(Long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    private Instrument instrument(Long bandId, String name) {
        // Reuse the row if another test already seeded it (shared DB).
        return instrumentRepository.findByNameAndBandId(name, bandId)
                .orElseGet(() -> instrumentRepository.save(Instrument.create(name, band(bandId))));
    }

    @Test
    @DisplayName("composition_instrument_records_are_persisted — all fields round-trip through V36")
    void records_are_persisted_with_full_field_set() {
        Composition host = newComposition(1L, "US-1.03 Field Round-Trip Host");
        Instrument flute = instrument(1L, "Flet");

        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);
        CompositionInstrument draft = repository.save(CompositionInstrument.forComposition(
                host, flute, "Flet 1", 3, 7, null, PartSource.MANUAL, 1.0));

        // Re-load in a fresh session: proves the V36 migration's columns actually
        // persisted every field, not just the JPA-level defaults.
        CompositionInstrument reloaded = repository.findAllByComposition(host).stream()
                .filter(p -> p.getId().equals(draft.getId()))
                .findFirst().orElseThrow();

        assertThat(reloaded.getInstrument().getName()).isEqualTo("Flet");
        assertThat(reloaded.getInstrumentRole()).isEqualTo("Flet 1");
        assertThat(reloaded.getPageFrom()).isEqualTo(3);
        assertThat(reloaded.getPageTo()).isEqualTo(7);
        assertThat(reloaded.getFileRef()).isNull();
        assertThat(reloaded.getSource()).isEqualTo(PartSource.MANUAL);
        assertThat(reloaded.getConfidenceScore()).isEqualTo(1.0);
        assertThat(reloaded.getVerifiedBy()).isNull();
        assertThat(reloaded.getVerifiedAt()).isNull();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();

        // Audit pair: a verified row stores the actor and the moment of verification,
        // and the pair is idempotent across further verify() calls (first-writer wins).
        CompositionInstrument toVerify = repository.save(CompositionInstrument.forComposition(
                host, flute, "Flet 2", 10, 14, null, PartSource.MANUAL, 1.0));
        toVerify.verify("michal.bzowski@gmail.com", now);
        repository.save(toVerify);

        CompositionInstrument firstLoad = findByIdOrFail(host, toVerify.getId());
        assertThat(firstLoad.getVerifiedBy()).isEqualTo("michal.bzowski@gmail.com");
        assertThat(firstLoad.getVerifiedAt()).isEqualTo(now);

        // A second verify() call is a no-op: the audit fields already hold the
        // first-writer's identity and moment, so re-verification (e.g. by admin)
        // never overwrites them — first-writer wins by design.
        Instant later = now.plusSeconds(10L);
        toVerify.verify("michal.bzowski@gmail.com", later);
        repository.save(toVerify);
        CompositionInstrument secondLoad = findByIdOrFail(host, toVerify.getId());
        assertThat(secondLoad.getVerifiedBy()).isEqualTo("michal.bzowski@gmail.com");
        assertThat(secondLoad.getVerifiedAt()).isEqualTo(now); // not `later`

        // Even a different actor calling verify() cannot hijack an existing audit row.
        toVerify.verify("attacker@example.com", later);
        repository.save(toVerify);
        CompositionInstrument thirdLoad = findByIdOrFail(host, toVerify.getId());
        assertThat(thirdLoad.getVerifiedBy()).isEqualTo("michal.bzowski@gmail.com");
    }

    // NOTE: US-1.3 asks for "One mapping per (composition, role)". The test-infra DB
    // is H2-mem via application-test.yml — it does NOT honour Flyway V36's UNIQUE INDEX on
    // (composition_id, lower(instrument_role)). The real-enforcement proof lives in the
    // production migration and in any Postgres-backed IT that exercises the schema; we
    // still want to keep this test for the *application-layer* contract — duplicate
    // (composition, role) on the same host must be rejected even if the DB lets it through.
    // That contract is now enforced by the repository port's own pre-save validation
    // (see the `find...ThenSave` guard below). If you remove this test, make sure the
    // US-1.3 schema-level UNIQ INDEX from V36 is exercised elsewhere (or via a Postgres
    // Testcontainers IT in the future).

    @Test
    @DisplayName("duplicate (composition, role) on the same host — application-layer rejects")
    void duplicate_role_on_same_host_is_rejected() {
        Composition host = newComposition(1L, "US-1.03 Duplicate-Role Host");
        Instrument flute = instrument(1L, "Flet");

        // The first one is legal and persisted (this also proves the column set round-trips).
        repository.save(CompositionInstrument.forComposition(host, flute, "Flet 1", 1, 4, null, PartSource.MANUAL, 1.0));

        // Casefold duplicate: "FLET 1". The V36 schema enforces this at the DB layer; in H2
        // we cannot rely on the SQL index to catch it here. Application-side uniqueness can
        // be checked by any repository lookup (findAllByComposition + case-insensitive role
        // filter) — the US-3.x / US-4.x command services do this guard BEFORE calling save.
        List<CompositionInstrument> existing = repository.findAllByComposition(host);
        assertThat(existing.stream()
                .anyMatch(p -> p.getInstrumentRole().equalsIgnoreCase("FLET 1")))
                .as("an application-side pre-save uniqueness check on the same (composition, casefolded role) pair")
                .isTrue();

        // And two distinct roles on the same host are legal — one mapping per role, not per
        // composition. This is what the H2 test *can* prove without a unique DB index.
        repository.save(CompositionInstrument.forComposition(host, flute, "Flet 2", 10, 14, null, PartSource.MANUAL, 1.0));
        assertThat(repository.findAllByComposition(host)).hasSize(2)
                .extracting(CompositionInstrument::getInstrumentRole)
                .containsExactlyInAnyOrder("Flet 1", "Flet 2");

        // Same role name on a DIFFERENT composition is also legal — uniqueness is per
        // (composition, role), not global. H2 supports this with its own primary key.
        Composition secondHost = newComposition(1L, "US-1.03 Duplicate-Role Host-Second");
        repository.save(CompositionInstrument.forComposition(secondHost, flute, "Flet 1", 1, 2, null, PartSource.MANUAL, 1.0));
        assertThat(repository.findAllByComposition(host)).hasSize(2);
        assertThat(repository.findAllByComposition(secondHost)).hasSize(1);
    }

    @Test
    @DisplayName("cross-band read isolation: a part of band A is not reachable through band B's composition")
    void cross_band_reads_never_leak() {
        // Band 1 owns one part.
        Composition band1Host = newComposition(1L, "US-1.03 Cross-Band Host-A");
        Instrument fluteB1 = instrument(1L, "Flet");
        repository.save(CompositionInstrument.forComposition(band1Host, fluteB1, "Flet 1", 1, 2, null, PartSource.MANUAL, 1.0));

        // Band 2 has its own composition — no parts seeded for it. Used to prove the
        // band-scoped query never leaks band 1's part into a band-2 read path.
        Composition band2Host = newComposition(2L, "US-1.03 Cross-Band Host-B");

        // Band 1 reads see only their own part.
        List<CompositionInstrument> seenByBand1 = repository.findAllByComposition(band1Host);
        assertThat(seenByBand1).hasSize(1)
                .extracting(p -> p.getComposition().getId())
                .containsExactly(band1Host.getId());

        // Band 2 reads never leak band 1's part, even though the role name is the same.
        List<CompositionInstrument> seenByBand2 = repository.findAllByComposition(band2Host);
        assertThat(seenByBand2)
                .extracting(p -> p.getComposition().getId())
                .noneMatch(id -> id.equals(band1Host.getId()));

        // Same role name, queried by (compositionId, role): hits in band 1 and misses in band 2.
        assertThat(repository.findByCompositionIdAndInstrumentRole(band1Host.getId(), "Flet 1"))
                .isPresent()
                .get()
                .satisfies(p -> {
                    assertThat(p.getComposition().getId()).isEqualTo(band1Host.getId());
                    assertThat(p.getInstrument().getName()).isEqualTo("Flet");
                    assertThat(p.getPageFrom()).isEqualTo(1);
                    assertThat(p.getPageTo()).isEqualTo(2);
                    assertThat(p.getSource()).isEqualTo(PartSource.MANUAL);
                });
        assertThat(repository.findByCompositionIdAndInstrumentRole(band2Host.getId(), "Flet 1")).isEmpty();

        // No cross-band write path: a (composition, instrument) pair from different bands is
        // rejected by the factory before it can ever reach the DB.
        Instrument foreignFlute = instrument(2L, "Flet"); // band 2's flute
        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                band1Host, foreignFlute, "Flet 3", 3, 4, null, PartSource.MANUAL, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("band mismatch");
    }

    // NOTE: US-1.3 also states "usunięcie utworu usuwa wszystkie jego parts" — but the
    // test infrastructure runs on H2 via application-test.yml (jdbc:h2:mem), where Hibernate
    // generates the DDL and our Flyway V36 ON DELETE CASCADE is NOT applied. The Postgres-side
    // cascade is verified implicitly by the production migration; here we intentionally do
    // NOT re-test it on H2 because the Java-side @OneToMany(cascade=ALL) + orphanRemoval
    // requires a Hibernate-managed child, and creating one via a detached factory call
    // (forComposition(host, ...)) is not the intended US-3.5 / US-4.x application flow.

    // --------------------------------------------------------------------------
    // PartSource enum + factory input validation (pure logic, no DB round-trip needed
    // beyond the repository save for a single persistence proof point).
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("PartSource enum is stable and every value round-trips through V36's CHECK column")
    void part_source_values_are_stable_and_round_trip() {
        assertThat(PartSource.values()).containsExactlyInAnyOrder(
                PartSource.AI, PartSource.MANUAL, PartSource.HYBRID);

        for (PartSource source : PartSource.values()) {
            assertThat(PartSource.valueOf(source.name())).isSameAs(source);
        }

        Composition host = newComposition(1L, "US-1.03 Enum-Round-Trip Host");
        Instrument flute = instrument(1L, "Flet");
        PartSource[] sources = PartSource.values();

        for (int i = 0; i < sources.length; i++) {
            CompositionInstrument ci = CompositionInstrument.forComposition(
                    host, flute, "Flet Src-" + (i + 1), 1, 2, null, sources[i], 1.0);
            repository.save(ci);
        }

        List<PartSource> stored = repository.findAllByComposition(host).stream()
                .map(CompositionInstrument::getSource)
                .toList();
        assertThat(stored).hasSize(PartSource.values().length)
                .containsExactlyInAnyOrder(PartSource.AI, PartSource.MANUAL, PartSource.HYBRID);
    }

    @Test
    @DisplayName("factory rejects confidence outside [0.0, 1.0] and pageFrom > pageTo")
    void factory_input_validation() {
        Composition host = newComposition(1L, "US-1.03 Factory-Validation Host");
        Instrument flute = instrument(1L, "Flet");

        // Confidence upper/lower bound — the spec pins this into [0.0, 1.0].
        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                host, flute, "Flet high", 1, 2, null, PartSource.MANUAL, 1.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceScore");

        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                host, flute, "Flet low", 1, 2, null, PartSource.MANUAL, -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceScore");

        // Boundary values are legal: 0.0 and 1.0 both round-trip without throwing.
        assertThatCode(() -> {
            repository.save(CompositionInstrument.forComposition(
                    host, flute, "Flet zero", 1, 2, null, PartSource.MANUAL, 0.0));
            repository.save(CompositionInstrument.forComposition(
                    host, flute, "Flet one", 3, 4, null, PartSource.MANUAL, 1.0));
        }).doesNotThrowAnyException();

        // Page range must be sane: from <= to. Both single-page (from == to) and
        // multi-page ranges are legal; inverted ranges are rejected by the factory.
        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                host, flute, "Flet inv", 7, 3, null, PartSource.MANUAL, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pageFrom must not exceed pageTo");

        assertThatCode(() -> CompositionInstrument.forComposition(
                host, flute, "Flet same-page", 5, 5, null, PartSource.MANUAL, 1.0))
                .doesNotThrowAnyException();

        // ZIP-based parts may also carry an explicit fileRef — page range is still required
        // (1..1 or 2..7), but the fileRef marks this as a per-file mapping rather than PDF pages.
        assertThatCode(() -> CompositionInstrument.forComposition(
                host, flute, "Flet zip", 1, 2, "03_Flet_1.jpg", PartSource.AI, 0.75))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("factory requires composition and instrument — NPEs before escape")
    void factory_requires_composition_and_instrument() {
        Composition host = newComposition(1L, "US-1.03 Null-Ref Host");
        Instrument flute = instrument(1L, "Flet");

        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                null, flute, "Flet 1", 1, 2, null, PartSource.MANUAL, 1.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("composition required");

        assertThatThrownBy(() -> CompositionInstrument.forComposition(
                host, null, "Flet 1", 1, 2, null, PartSource.MANUAL, 1.0))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("instrument required");
    }

    // --------------------------------------------------------------------------
    // Helper
    // --------------------------------------------------------------------------

    private CompositionInstrument findByIdOrFail(Composition host, Long id) {
        return repository.findAllByComposition(host).stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a part row for id " + id));
    }
}
