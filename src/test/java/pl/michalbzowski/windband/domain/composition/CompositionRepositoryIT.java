package pl.michalbzowski.windband.domain.composition;

import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    @Test
    void search_inBand_returnsOnlyMatchingCompositionsOfThatBand() {
        Band bandA = bandRepository.findById(1L).orElseThrow();
        Band bandB = bandRepository.findById(2L)
            .orElseGet(() -> bandRepository.save(Band.create("Zespół B-" + System.nanoTime(), "zespB" + System.nanoTime())));

        // one composition per band, sharing a matching search term in different fields …
        Composition compA = repository.save(Composition.create("Walc z XIX wieku", null, "Composer A", null, bandA));
        Composition compB = repository.save(Composition.create("Inny utwór B", null, null, "Composer B", bandB));

        // when searching per band by that term …
        List<Composition> resultsA = repository.search(1L, "composer");
        List<Composition> resultsB = repository.search(2L, "composer");

        // then each band sees only its own composition (band isolation in the search path)
        assertThat(resultsA).extracting(Composition::getId).contains(compA.getId()).doesNotContain(compB.getId());
        assertThat(resultsA).allMatch(c -> c.getBand().getId().equals(bandA.getId()));
        assertThat(resultsB).extracting(Composition::getId).contains(compB.getId()).doesNotContain(compA.getId());
        assertThat(resultsB).allMatch(c -> c.getBand().getId().equals(bandB.getId()));
    }

    @Test
    void search_returnsEmptyWhenNoMatchesInThatBand() {
        repository.save(Composition.create("Utwór A", null, null, "aranżyk", bandRepository.findById(1L).orElseThrow()));
        assertThat(repository.search(2L, "aranżer")).isEmpty(); // matches band 1's row (partial), not band 2
    }

    @Test
    void findAllByBand_neverLeaksCompositionsOfOtherBands() {
        Band bandA = bandRepository.findById(1L).orElseThrow();
        Band bandB = bandRepository.findById(2L)
            .orElseGet(() -> bandRepository.save(Band.create("Zespół B-" + System.nanoTime(), "zespB" + System.nanoTime())));

        Composition compA = repository.save(Composition.create("Wspólny tytuł-walczę", null, null, null, bandA));
        Composition compB = repository.save(Composition.create("Wspólny tytuł-bis", null, null, null, bandB));

        List<Composition> ofBandA = repository.findAllByBand(bandA);
        List<Composition> ofBandB = repository.findAllByBand(bandB);

        assertThat(ofBandA).extracting(Composition::getId).contains(compA.getId()).doesNotContain(compB.getId());
        assertThat(ofBandB).extracting(Composition::getId).contains(compB.getId()).doesNotContain(compA.getId());
    }
}
