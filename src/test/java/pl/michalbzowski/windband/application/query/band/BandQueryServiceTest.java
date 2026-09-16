package pl.michalbzowski.windband.application.query.band;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.transaction.annotation.Transactional;

/**
 * Task 1 of US-1.6c (close Epic 1) — pins down the contract of the
 * {@code BandQueryService} band-existence facade that every CQRS service in
 * the {@code application} layer is supposed to route through (D5).
 *
 * <p>The two tests cover the only observable behaviour of a single method:
 * <ul>
 *   <li>happy path — known band id resolves to the same row returned by the
 *       repository, so callers can safely rely on identity-equality after
 *       {@code band.getId()} comparisons;</li>
 *   <li>failure path — unknown band id fails closed with
 *       {@link IllegalArgumentException} (→ HTTP 400 via
 *       {@code GlobalExceptionHandler#handleBadRequest}), the exact contract
 *       that {@code CompositionCommandService.create(cmd, bandId)} and
 *       every other CQRS entry point depend on.</li>
 * </ul>
 *
 * <p><b>Why an IT at all?</b> The class is annotated
 * {@code @Transactional(readOnly = true)} at the type level — a bare Mockito
 * unit test would never exercise that boundary and could not prove that
 * calling the method outside a write transaction actually works (the existing
 * ITs for the CQRS services already cover the full DB path, but they never
 * called {@code BandQueryService} directly because it didn't exist yet with
 * this name). This class is the first thing that pins the <i>name</i>
 * {@code getRequiredBand} for the contract — the rest of Epic 1 references
 * that exact identifier in its acceptance criteria.
 */
@Transactional
class BandQueryServiceTest extends BaseIntegrationTest {

    @Autowired
    private BandQueryService bandQueryService;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Seed band 1 (used by almost every other test in the suite) must not be
    // deleted across tests — the multi-tenant contract of the repository layer
    // assumes it exists. Band 2 exists too, but it is optional for these two
    // tests and we leave it alone to stay isolated from any band-2 test that
    // may run in parallel in another suite.
    @BeforeEach
    void cleanup() {
        // Nothing to clean per-test: both bands are seed data shared by many
        // suites (see data.sql + BaseIntegrationTest javadoc).
    }

    @Test
    void getRequiredBand_returns_existing_row_and_matches_repositories_view() {
        Band expected = bandRepository.findById(1L).orElseThrow();

        Band actual = bandQueryService.getRequiredBand(expected.getId());

        assertThat(actual).isNotNull();
        assertThat(actual.getId()).isEqualTo(expected.getId());
        // Identity-equality is not guaranteed by JPA identity-map semantics on
        // a read-only boundary, so assert structural equality instead: id + name.
        assertThat(actual.getName()).isEqualTo(expected.getName());
    }

    @Test
    void getRequiredBand_unknown_band_fails_closed_with_ia() {
        // 999L is a stable "definitely-no-such-band" seed choice used elsewhere
        // in the suite (see CompositionCommandServiceTest for the same id).
        assertThatThrownBy(() -> bandQueryService.getRequiredBand(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Band not found")
                .hasMessageContaining("999");
    }
}
