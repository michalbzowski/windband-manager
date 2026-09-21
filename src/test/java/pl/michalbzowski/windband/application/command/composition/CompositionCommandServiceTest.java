package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionInstrument;
import pl.michalbzowski.windband.domain.composition.CompositionInstrumentRepository;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;
import pl.michalbzowski.windband.domain.composition.PartSource;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.composition.ScoreFile;
import pl.michalbzowski.windband.domain.composition.ScoreFileRepository;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.transaction.annotation.Transactional;

/**
 * Task 1.03 — composition command service (create / update / archive / restore)
 * with strict band isolation and explicit failure paths:
 * <ul>
 *   <li>blank title is rejected before any DB write</li>
 *   <li>over-long title is rejected by the domain factory ({@code IllegalArgument})</li>
 *   <li>unknown band id on create fails with {@code IllegalArgumentException} (→ 400)</li>
 *   <li>updating / archiving a composition owned by another band fails (→ 409,
 *       no cross-band access — US-1xx multi-tenant contract)</li>
 * </ul>
 */
@Transactional
class CompositionCommandServiceTest extends BaseIntegrationTest {

    @Autowired
    private CompositionCommandService commandService;

    @Autowired
    private CompositionRepository repository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CompositionInstrumentRepository compositionInstrumentRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private ScoreFileRepository scoreFileRepository;

    /**
     * The Testcontainers PostgreSQL is shared across test classes in the same
     * surefire JVM. Several earlier classes ({@code CompositionInstrumentIT},
     * {@code ScoreFileCommandServiceIT}, UI tests) are intentionally
     * non-transactional — they COMMIT compositions/score_files that survive
     * into this class's context. Our count-based assertions (e.g.
     * "exactly one in band") would otherwise see those committed rows and
     * fail. Clearing children first (V35 FK forbids a hard parent delete
     * while score_files references the composition) then clearing parents
     * restores a clean state for each test. This class is itself @Transactional,
     * so its own rows still roll back — the cleanup only handles foreign data.
     */
    @BeforeEach
    void cleanCompositions() {
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    // ---- create ----------------------------------------------------------

    @Test
    void create_should_persist_with_metadata_and_draft_status() {
        Instant before = Instant.now();

        CreateCompositionCommand cmd = new CreateCompositionCommand();
        cmd.setTitle("Nowy utwór");
        cmd.setDescription("Opis utworu");
        cmd.setComposer("Kompozytor");
        cmd.setArranger("Aragonista");

        Composition saved = commandService.create(cmd, 1L);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Nowy utwór");
        assertThat(saved.getDescription()).isEqualTo("Opis utworu");
        assertThat(saved.getComposer()).isEqualTo("Kompozytor");
        assertThat(saved.getArranger()).isEqualTo("Aragonista");
        assertThat(saved.getStatus()).isEqualTo(CompositionStatus.DRAFT);

        // lifecycle timestamps are populated by JPA callbacks (@PrePersist)
        assertThat(saved.getCreatedAt()).isInstanceOf(Instant.class).isNotNull().isAfterOrEqualTo(before);
        assertThat(saved.getUpdatedAt()).isInstanceOf(Instant.class).isNotNull();

        List<Composition> inBand =
                repository.findAllByBand(bandRepository.findById(1L).orElseThrow());
        assertThat(inBand).hasSize(1);
    }

    @Test
    void create_should_reject_blank_title() {
        CreateCompositionCommand cmd = new CreateCompositionCommand();
        cmd.setTitle("   ");
        cmd.setComposer("x");

        assertThatThrownBy(() -> commandService.create(cmd, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tytuł jest wymagany");

        // nothing was persisted
        assertThat(repository.findAllByBand(bandRepository.findById(1L).orElseThrow()))
                .isEmpty();
    }

    @Test
    void create_should_reject_over_long_title() {
        CreateCompositionCommand cmd = new CreateCompositionCommand();
        // 201 chars: the service no longer re-checks length itself — the domain
        // factory (Composition.create → requireTitle) throws the IAE first.
        cmd.setTitle("t".repeat(201));

        assertThatThrownBy(() -> commandService.create(cmd, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    void create_should_fail_when_band_not_found() {
        CreateCompositionCommand cmd = new CreateCompositionCommand();
        cmd.setTitle("Bez banda");

        // unknown band at create() is a pure input error (400), not a cross-band conflict.
        assertThatThrownBy(() -> commandService.create(cmd, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Band not found")
                .hasMessageContaining("999");
    }

    // ---- update ----------------------------------------------------------

    @Test
    void update_should_change_metadata_in_place() {
        Composition seed = commandService.create(title("Seed"), 1L);

        UpdateCompositionCommand cmd = new UpdateCompositionCommand();
        cmd.setTitle("Zmieniony");
        cmd.setArranger("Nowy aranżer"); // null for the others = leave unchanged

        Composition updated = commandService.update(seed.getId(), cmd, 1L);

        assertThat(updated.getId()).isEqualTo(seed.getId());
        assertThat(updated.getTitle()).isEqualTo("Zmieniony");
        assertThat(updated.getComposer()).isNull(); // null means leave unchanged
        assertThat(updated.getArranger()).isEqualTo("Nowy aranżer");
    }

    /**
     * A composition id that does not resolve in the calling band is treated as a
     * multi-tenant conflict (409) — either another band owns it, or it simply does not exist.
     */
    @Test
    void update_should_reject_unknown_or_cross_band_id() {
        UpdateCompositionCommand cmd = new UpdateCompositionCommand();
        cmd.setTitle("x");

        assertThatThrownBy(() -> commandService.update(999L, cmd, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");
    }

    @Test
    void update_should_block_cross_band_access() {
        // Seed lives in band 1.
        Composition seed = commandService.create(title("Band-1-only"), 1L);
        // A second band must exist to prove isolation — the test seed has one (see data.sql).
        var other = bandRepository.findById(2L).orElseThrow();

        UpdateCompositionCommand cmd = new UpdateCompositionCommand();
        cmd.setTitle("Hacked");

        assertThatThrownBy(() -> commandService.update(seed.getId(), cmd, other.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");
    }

    // ---- delete (US-1.6 AC) ----------------------------------------------

    @Test
    void delete_should_remove_row_and_fall_through_cross_band() {
        Composition saved = commandService.create(title("Do usunięcia"), 1L);
        Long savedId = saved.getId();

        commandService.deleteComposition(savedId, 1L);

        assertThat(repository.findByIdAndBandId(savedId, 1L)).isEmpty();

        // cross-band — fail closed (409) and the row survives:
        Composition mine = commandService.create(title("Tylko band-1"), 1L);
        assertThatThrownBy(() -> commandService.deleteComposition(mine.getId(), 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");
        assertThat(repository.findByIdAndBandId(mine.getId(), 1L)).isPresent();
    }

    // ---- archive / restore ----------------------------------------------

    @Test
    void archive_should_set_status_archived_and_restore_should_revert_to_draft() {
        Composition c = commandService.create(title("Arch"), 1L);
        assertThat(c.getStatus()).isEqualTo(CompositionStatus.DRAFT).as("fresh composition starts DRAFT");

        commandService.archive(c.getId(), 1L);
        assertThat(repository.findByIdAndBandId(c.getId(), 1L).orElseThrow().getStatus())
                .isEqualTo(CompositionStatus.ARCHIVED);

        commandService.restore(c.getId(), 1L);
        assertThat(repository.findByIdAndBandId(c.getId(), 1L).orElseThrow().getStatus())
                .isEqualTo(CompositionStatus.DRAFT);
    }

    @Test
    void archive_should_block_cross_band_access() {
        var c = commandService.create(title("Only-band-1"), 1L);
        var otherBandId = bandRepository.findById(2L)
                .map(b -> b.getId())
                .orElseGet(() -> 9999L); // if no second seed band, use an id that fails isolation

        assertThatThrownBy(() -> commandService.archive(c.getId(), otherBandId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");
    }

    // ---- verify-gate (US-3.03) -------------------------------------------

    @Test
    void verifyParts_should_verify_all_unverified_parts_and_promote_to_ready() {
        Composition c = seedComposition(1L, "Verify happy-path");
        Instrument flute = instrumentInBand(1L, "Flet");
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 1", 1, 3, null, PartSource.MANUAL, 1.0));
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 2", 5, 9, null, PartSource.MANUAL, 1.0));

        commandService.verifyCompositionParts(c.getId(), 1L, "admin@example.com");

        Composition reloaded = repository.findByIdAndBandId(c.getId(), 1L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CompositionStatus.READY);
        List<CompositionInstrument> parts = compositionInstrumentRepository.findAllByComposition(reloaded);
        assertThat(parts).hasSize(2).allSatisfy(p -> {
            assertThat(p.getVerifiedBy()).isEqualTo("admin@example.com");
            assertThat(p.getVerifiedAt()).isNotNull();
        });
    }

    @Test
    void verifyParts_should_refuse_blank_verifier_and_change_nothing() {
        Composition c = seedComposition(1L, "Verify blank-verifier");
        Instrument flute = instrumentInBand(1L, "Flet");
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 1", 1, 3, null, PartSource.MANUAL, 1.0));

        assertThatThrownBy(() -> commandService.verifyCompositionParts(c.getId(), 1L, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verifier");

        // No side effects of any kind: composition still DRAFT, the part row untouched.
        Composition reloaded = repository.findByIdAndBandId(c.getId(), 1L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CompositionStatus.DRAFT);
        List<CompositionInstrument> parts = compositionInstrumentRepository.findAllByComposition(reloaded);
        assertThat(parts).hasSize(1).allSatisfy(p -> {
            assertThat(p.getVerifiedBy()).isNull();
            assertThat(p.getVerifiedAt()).isNull();
        });
    }

    @Test
    void verifyParts_should_fail_closed_on_cross_band_access() {
        Composition c = seedComposition(1L, "Verify cross-band");
        Instrument flute = instrumentInBand(1L, "Flet");
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 1", 1, 3, null, PartSource.MANUAL, 1.0));

        var otherBand = bandRepository.findById(2L).orElseThrow();
        assertThatThrownBy(() -> commandService.verifyCompositionParts(c.getId(), otherBand.getId(), "attacker@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");

        // Neither the composition status nor any part row may have moved.
        Composition reloaded = repository.findByIdAndBandId(c.getId(), 1L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CompositionStatus.DRAFT);
        List<CompositionInstrument> parts = compositionInstrumentRepository.findAllByComposition(reloaded);
        assertThat(parts).hasSize(1).allSatisfy(p -> {
            assertThat(p.getVerifiedBy()).isNull();
            assertThat(p.getVerifiedAt()).isNull();
        });
    }

    @Test
    void verifyParts_should_preserve_prior_auditor_and_promote_only_when_all_verified() {
        Composition c = seedComposition(1L, "Verify idempotent re-entry");
        Instrument flute = instrumentInBand(1L, "Flet");
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 1", 1, 3, null, PartSource.MANUAL, 1.0));
        compositionInstrumentRepository.save(
                CompositionInstrument.forComposition(c, flute, "Flet 2", 5, 9, null, PartSource.MANUAL, 1.0));

        // Simulate a partial prior verification by a different actor on one of the two parts —
        // the audit pair is frozen to them and must survive a later full-verify pass.
        CompositionInstrument flet1 = compositionInstrumentRepository
                .findByCompositionIdAndInstrumentRole(c.getId(), "Flet 1")
                .orElseThrow();
        Instant earlierAudit = Instant.ofEpochMilli(1_700_000_000_000L);
        flet1.verify("first@example.com", earlierAudit);
        compositionInstrumentRepository.save(flet1);

        commandService.verifyCompositionParts(c.getId(), 1L, "second@example.com");

        Composition reloaded = repository.findByIdAndBandId(c.getId(), 1L).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CompositionStatus.READY); // now fully verified
        List<CompositionInstrument> parts = compositionInstrumentRepository.findAllByComposition(reloaded);
        parts.stream().filter(p -> p.getInstrumentRole().equals("Flet 1"))
                .findFirst().ifPresent(p -> {
                    assertThat(p.getVerifiedBy()).isEqualTo("first@example.com"); // first-writer wins, not hijacked
                    assertThat(p.getVerifiedAt()).isEqualTo(earlierAudit);
                });
        parts.stream().filter(p -> p.getInstrumentRole().equals("Flet 2"))
                .findFirst().ifPresent(p -> {
                    assertThat(p.getVerifiedBy()).isEqualTo("second@example.com");
                    assertThat(p.getVerifiedAt()).isNotNull();
                });
    }



    @Test
    void addPart_withNullScoreFileId_shouldLeaveTheColumnUnset() {
        Long bandId = 1L;
        var comp = seedComposition(bandId, "Legacy manual add (no explicit file)");
        Instrument insp = instrumentInBand(bandId, "Sax II");
        var result = commandService.addPart(comp.getId(), insp.getId(), "Sax II", 1, 4, null, null, bandId);
        assertThat(result.getScoreFile()).isNull();
        ScoreFile inDb = compositionInstrumentRepository.findById(result.getId()).orElseThrow().getScoreFile();
        assertThat(inDb).isNull();
    }

    // helper ---------------------------------------------------------------

    private Composition seedComposition(Long bandId, String title) {
        var cmd = new CreateCompositionCommand();
        cmd.setTitle(title);
        return commandService.create(cmd, bandId);
    }

    private Instrument instrumentInBand(Long bandId, String name) {
        // Reuse the row if another test already seeded it (shared Testcontainers DB).
        return instrumentRepository.findByNameAndBandId(name, bandId)
                .orElseGet(() -> instrumentRepository.save(Instrument.create(name, bandRepository.findById(bandId).orElseThrow())));
    }

    private static CreateCompositionCommand title(String t) {
        var cmd = new CreateCompositionCommand();
        cmd.setTitle(t);
        return cmd;
    }
}
