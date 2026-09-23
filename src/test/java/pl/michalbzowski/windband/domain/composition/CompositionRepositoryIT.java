package pl.michalbzowski.windband.domain.composition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@link CompositionRepository} port — verifies that
 * the Spring Data adapter enforces band isolation and supports a band-scoped
 * search (US-1.02) across title, composer and arranger.
 */
class CompositionRepositoryIT extends BaseIntegrationTest {

    @Autowired
    private CompositionRepository repository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateCompositions() {
        // Clean slate BEFORE each test: sibling suites (upload / analysis ITs) commit
        // rows through the real HTTP stack and share this Postgres JVM — without a
        // pre-wipe the first test here sees their leftover compositions
        // (CI run 683: "expected size: 1 but was: 10").
        // Children first → parent; test DDL is Hibernate-generated so Flyway's
        // ON DELETE CASCADE does not apply here.
        jdbcTemplate.execute("DELETE FROM event_compositions");
        jdbcTemplate.execute("DELETE FROM composition_instruments");
        jdbcTemplate.execute("DELETE FROM score_analysis");
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    @AfterEach
    void cleanup() {
        // Self-contained: tests here seed rows and must remove them, so the shared
        // Testcontainers DB never leaks data across test classes.
        // Children first → parent (surefire reuses one JVM/DB; other suites commit
        // score_files rows that block a bare DELETE FROM compositions on the FK).
        jdbcTemplate.execute("DELETE FROM event_compositions");
        jdbcTemplate.execute("DELETE FROM composition_instruments");
        jdbcTemplate.execute("DELETE FROM score_analysis");
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    private Band band(Long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    @Test
    void search_inBand_returnsOnlyMatchingCompositionsOfThatBand() {
        // seed one composition per band so the search term matches a real row in each group.
        repository.save(Composition.create("Walc z XIX wieku", null, "Composer A", null, band(1L)));
        repository.save(Composition.create("Inny utwór B", null, null, "Composer B", band(2L)));

        // when searching per band by a shared term …
        List<Composition> resultsA = repository.search(1L, "composer");
        List<Composition> resultsB = repository.search(2L, "composer");

        // then each band sees only its own row; no cross-band leakage in either direction.
        assertThat(resultsA).hasSize(1)
                .extracting(Composition::getBand)
                .allMatch(b -> b.getId().equals(1L));
        assertThat(resultsB).hasSize(1)
                .extracting(Composition::getBand)
                .allMatch(b -> b.getId().equals(2L));
    }

    @Test
    void search_returnsEmptyWhenNoMatchesInThatBand() {
        // A row that matches the term lives in band 1 only.
        repository.save(Composition.create("Utwór A", null, null, "aranżyk", band(1L)));

        assertThat(repository.search(2L, "aranżer")).isEmpty();
        // and the same term does match for its own band (positive control).
        assertThat(repository.search(1L, "aranżyk")).hasSize(1);
    }

    @Test
    void findAllByBand_neverLeaksCompositionsOfOtherBands() {
        repository.save(Composition.create("Wspólny tytuł-walczę", null, null, null, band(1L)));
        repository.save(Composition.create("Wspólny tytuł-bis", null, null, null, band(2L)));

        List<Composition> ofBandA = repository.findAllByBand(band(1L));
        List<Composition> ofBandB = repository.findAllByBand(band(2L));

        assertThat(ofBandA).hasSize(1).extracting(Composition::getBand)
                .allMatch(b -> b.getId().equals(1L));
        assertThat(ofBandB).hasSize(1).extracting(Composition::getBand)
                .allMatch(b -> b.getId().equals(2L));
    }
}
