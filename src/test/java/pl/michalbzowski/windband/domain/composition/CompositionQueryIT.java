package pl.michalbzowski.windband.domain.composition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for US-1.02 (list & search) over the {@link CompositionRepository}
 * port — verifies band-scoped listing (newest update first, archivable by status),
 * title/composer/arranger matching, and that no composition ever leaks across bands.
 */
class CompositionQueryIT extends BaseIntegrationTest {

    @Autowired
    private CompositionRepository repository;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private final List<Long> createdCompositionIds = new java.util.ArrayList<>();

    @BeforeEach
    void truncateCompositions() {
        // Isolate this test class from leftover rows of other tests in the shared container.
        // Children first → parent: sibling suites commit score_files / instrument rows that
        // FK-block a bare DELETE FROM compositions (test DDL is Hibernate-generated, so the
        // Flyway ON DELETE CASCADE does not apply here).
        jdbcTemplate.execute("DELETE FROM event_compositions");
        jdbcTemplate.execute("DELETE FROM composition_instruments");
        jdbcTemplate.execute("DELETE FROM score_analysis");
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    @AfterEach
    void cleanup() {
        for (Long id : createdCompositionIds) {
            repository.findByIdAndBandId(id, 1L).ifPresent(repository::delete);
        }
        createdCompositionIds.clear();
        // Same child→parent wipe as the preceding hook — a failed test leaves its seed rows.
        jdbcTemplate.execute("DELETE FROM event_compositions");
        jdbcTemplate.execute("DELETE FROM composition_instruments");
        jdbcTemplate.execute("DELETE FROM score_analysis");
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    private Band band(Long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    private Composition save(String title, String composer, String arranger, Long bandId) {
        Composition c = repository.save(Composition.create(title, null, composer, arranger, band(bandId)));
        createdCompositionIds.add(c.getId());
        return c;
    }

    @Test
    @DisplayName("findAllByBand returns only the band's compositions, newest update first")
    void listByBand_isolatedAndSortedDesc() {
        save("Stary utwór", null, null, 1L);
        Composition old = save("Walc z XIX wieku", null, null, 1L);
        // Touch the row so it becomes the most recently updated.
        old.updateTexts(null, "aktualizacja opisu", null, null);
        repository.save(old);
        save("Nowy utwór", null, null, 2L);

        Band b1 = band(1L);
        List<Composition> ofBand1 = repository.findAllByBand(b1);
        List<Composition> ofBand2 = repository.findAllByBand(band(2L));

        // No cross-band leakage in either direction, and getBand() is safe (lazy assoc initialised by the adapter).
        assertThat(ofBand1).allMatch(c -> c.getBand().getId().equals(1L));
        assertThat(ofBand1).extracting(Composition::getId).contains(old.getId());
        assertThat(ofBand2).allMatch(c -> c.getBand().getId().equals(band(2L).getId()));
        assertThat(ofBand2).extracting(Composition::getId).doesNotContain(old.getId());

        // US-1.02: default sort is newest-update first.
        assertThat(ofBand1.get(0).getUpdatedAt()).isAfterOrEqualTo(ofBand1.get(1).getUpdatedAt());
    }

    @Test
    @DisplayName("searchByTitle matches on title, composer and arranger — case-insensitively")
    void search_matchesOnAllThreeTextFields() {
        save("Kwartet dla zespołu", "Fr. Liszt", "J. Karłowicz", 1L);
        save("Utwór bez danych tekstowych", null, null, 1L);

        List<Composition> byTitle = repository.search(1L, "kwartet");
        assertThat(byTitle).extracting(Composition::getTitle).containsExactly("Kwartet dla zespołu");
        // getBand() must be safe: the adapter initialises the lazy association.
        assertThat(byTitle).allMatch(c -> c.getBand().getId().equals(1L));

        List<Composition> byComposerCaseInsensitive = repository.search(1L, "fr. LISZT");
        assertThat(byComposerCaseInsensitive).extracting(Composition::getId)
                .contains(byTitle.get(0).getId());

        List<Composition> byArranger = repository.search(1L, "karłowicz");
        assertThat(byArranger).extracting(Composition::getId)
                .contains(byTitle.get(0).getId());

        assertThat(repository.search(2L, "kwartet")).isEmpty(); // isolation across bands
    }

    @Test
    @DisplayName("searchByTitle returns empty for a term without matches")
    void search_noMatches() {
        save("Jeden utwór", null, null, 1L);
        assertThat(repository.search(1L, "xyzplxy")).isEmpty();
    }

    @Test
    @DisplayName("findByIdAndBandId never leaks another band's composition (US-1.01 acceptance 1)")
    void crossBandLookupIsEmpty() {
        Composition ofBand2 = save("Tajny utwór B", null, null, 2L);

        Optional<Composition> inBandA = repository.findByIdAndBandId(ofBand2.getId(), band(1L).getId());
        assertThat(inBandA).isEmpty();

        Optional<Composition> inBandB = repository.findByIdAndBandId(ofBand2.getId(), band(2L).getId());
        assertThat(inBandB).isPresent().hasValueSatisfying(c ->
                assertThat(c.getBand().getId()).isEqualTo(band(2L).getId()));
    }

    @Test
    @DisplayName("existsByIdAndBandId respects the band scope")
    void exists_isBandScoped() {
        Composition c = save("Istniejący utwór", null, null, 1L);
        assertThat(repository.existsByIdAndBandId(c.getId(), 1L)).isTrue();
        assertThat(repository.existsByIdAndBandId(c.getId(), 2L)).isFalse();
    }

    @Test
    @DisplayName("save then findByIdAndBandId round-trips every field")
    void saveAndFind_roundTrip() {
        Band b1 = band(1L);
        Composition c = save("Odwrot-utwór", "Kompozytor Test", "Aranżer Test", 1L);

        Optional<Composition> loadedOpt = repository.findByIdAndBandId(c.getId(), b1.getId());
        assertThat(loadedOpt).isPresent();
        Composition loaded = loadedOpt.get();
        assertThat(loaded.getTitle()).isEqualTo("Odwrot-utwór");
        assertThat(loaded.getComposer()).isEqualTo("Kompozytor Test");
        assertThat(loaded.getArranger()).isEqualTo("Aranżer Test");
        assertThat(loaded.getBand().getId()).isEqualTo(b1.getId());
        assertThat(loaded.getStatus()).isEqualTo(CompositionStatus.DRAFT);
    }
}
