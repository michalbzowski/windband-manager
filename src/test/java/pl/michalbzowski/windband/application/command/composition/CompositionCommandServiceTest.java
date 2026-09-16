package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.BandRepository;

import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

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

    @BeforeEach
    void cleanCompositions() {
        // Only compositions are feature-scoped here; keep seed band intact.
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

    // helper ---------------------------------------------------------------

    private static CreateCompositionCommand title(String t) {
        var cmd = new CreateCompositionCommand();
        cmd.setTitle(t);
        return cmd;
    }
}
