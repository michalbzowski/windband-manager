package pl.michalbzowski.windband.domain.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;

/**
 * Integration tests for the {@link Composition} aggregate against a real
 * (Testcontainers) database — exercises Flyway V32, JPA mapping and band isolation.
 */
@Transactional
class CompositionIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private CompositionRepository repository;

    private Band band(Long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    @Test
    @DisplayName("save persists all fields and fills createdAt/updatedAt (V32 schema)")
    void persists_fields_and_timestamps() {
        Composition saved = repository.save(Composition.create(
                "Persistence Check", "desc", "Comp", "Arr", band(1L)));

        assertThat(saved.getId()).isNotNull();
        Band reloaded = bandRepository.findById(1L).orElseThrow();
        assertThat(bandRepository.findAll())
                .extracting(Band::getId)
                .contains(saved.getBand().getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CompositionStatus.DRAFT);
        assertThat(reloaded.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findAllByBand returns only that band's compositions (no leakage)")
    void isolation_between_bands() {
        repository.save(Composition.create("Only in Band 1", null, null, null, band(1L)));
        repository.save(Composition.create("A composition of other band", "x", "y", "z", band(2L)));

        Band b1 = band(1L);
        Band b2 = band(2L);

        List<Composition> ofBand1 = repository.findAllByBand(b1);
        List<Composition> ofBand2 = repository.findAllByBand(b2);

        assertThat(ofBand1).extracting(Composition::getTitle)
                .contains("Only in Band 1")
                .doesNotContain("A composition of other band");
        assertThat(ofBand2).extracting(Composition::getTitle)
                .contains("A composition of other band")
                .doesNotContain("Only in Band 1");
    }

    @Test
    @DisplayName("existsByIdAndBandId respects band scope")
    void existsScopedToBand() {
        Composition c = repository.save(Composition.create("Exists?" , null, null, null, band(1L)));

        assertThat(repository.existsByIdAndBandId(c.getId(), 1L)).isTrue();
        assertThat(repository.existsByIdAndBandId(c.getId(), 2L)).isFalse(); // other band, same id -> false
    }
}
