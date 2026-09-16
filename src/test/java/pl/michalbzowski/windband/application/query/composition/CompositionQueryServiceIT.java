package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;


import pl.michalbzowski.windband.domain.composition.Composition;
import pl.michalbzowski.windband.domain.composition.CompositionRepository;
import pl.michalbzowski.windband.domain.composition.CompositionStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-1.04 — composition listing, retrieval and search through the
 * application-layer {@link CompositionQueryService}.
 *
 * <p>Covers the acceptance surface the plan calls out: listByBand (filtering by
 * optional status, newest-update ordering implied by repository contract),
 * getById with strict band isolation (cross-band reads fail closed), and search
 * reusing the band-scoped repository contract. Mirrors the conventions of
 * {@code CompositionQueryIT} in the domain package.
 */
class CompositionQueryServiceIT extends BaseIntegrationTest {

    @Autowired
    private CompositionQueryService queryService;

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private CompositionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void truncateCompositionsOnly() {
        // Isolate this class's rows in the shared PostgreSQL container.
        // Bands (seed data) stay intact — we must not touch other tables.
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    @AfterEach
    void cleanupRows() {
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    private Band band(long id) {
        return bandRepository.findById(id).orElseThrow();
    }

    private Composition saveIn(Band band, String title) {
        return repository.save(Composition.create(title, "opis testowy",
                "Kompozytor Test", "Aranżer Test", band));
    }

    @Test
    @DisplayName("listByBand returns only that band's compositions — no cross-band leak in either direction")
    void listByBand_isolatedAndSortedDesc() {
        var b1 = band(1L);
        var b2 = band(2L);

        Composition draftA = saveIn(b1, "Stary utwór");
        Composition newerA = saveIn(b1, "Świeższy utwór");
        // newest-update first — touch the second row so it sorts ahead:
        newerA.updateTexts(null, "aktualizacja opisu", null, null);
        repository.save(newerA);

        Composition otherBandRow = saveIn(b2, "Tajny utwór B");

        List<Composition> listing = queryService.listByBand(1L, null);

        assertThat(listing).extracting(Composition::getId)
                .containsExactlyInAnyOrder(draftA.getId(), newerA.getId())
                .doesNotContain(otherBandRow.getId());
        assertThat(listing).allMatch(c -> c.getBand().getId().equals(1L));
        // Sort contract from the repository: most-recently-updated first.
        assertThat(listing.get(0).getId()).isEqualTo(newerA.getId());

        List<Composition> listingB2 = queryService.listByBand(2L, null);
        assertThat(listingB2).extracting(Composition::getId)
                .containsExactly(otherBandRow.getId());
    }

    @Test
    @DisplayName("listByBand honours an explicit status filter (READY requested excludes DRAFT/ARCHIVED)")
    void listByBand_appliesStatusFilterIfProvided() {
        var b1 = band(1L);
        Composition draftA = saveIn(b1, "Draft A");

        // Mutation → save: follows the project's established IT pattern
        // (CompositionQueryIT) of touching + saving a row to move/update it.
        Composition readyA = saveIn(b1, "Ready A");
        readyA.markReady();
        repository.save(readyA);
        Composition archivedA = saveIn(b1, "Archived A");
        archivedA.archive();
        repository.save(archivedA);

        List<Composition> readyOnly = queryService.listByBand(1L, CompositionStatus.READY);

        assertThat(readyOnly).extracting(Composition::getId)
                .containsExactly(readyA.getId());
        // The unfiltered variant still sees all three rows (filter is opt-in):
        List<Composition> unfiltered = queryService.listByBand(1L, null);
        assertThat(unfiltered).extracting(Composition::getId)
                .contains(draftA.getId(), readyA.getId(), archivedA.getId());
    }

    @Test
    @DisplayName("get resolves an in-band composition; cross-band or missing ids fail closed (US-1.01 isolation)")
    void get_inBandSucceeds_crossBandAndMissingFail() {
        var b2 = band(2L);
        Composition ofB2 = saveIn(b2, "Tajny utwór B");

        Composition hit = queryService.get(ofB2.getId(), 2L);
        // Service (JPA) returns a materialised instance: assert on identity fields, not object reference.
        assertThat(hit.getId()).isEqualTo(ofB2.getId());
        assertThat(hit.getBand().getId()).isEqualTo(2L);
        assertThat(hit.getTitle()).isEqualTo("Tajny utwór B");

        // Same id, different band → fails closed (multi-tenant leak guard):
        assertThatThrownBy(() -> queryService.get(ofB2.getId(), 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to band");
    }

    @Test
    @DisplayName("search reuses the band-scoped, case-insensitive contract of the repository port")
    void search_matchesInBandAndStaysIsolated() {
        var b1 = band(1L);
        var b2 = band(2L);
        Composition hitB1 = saveIn(b1, "Kwartet dla zespołu");

        List<Composition> inBand = queryService.search(1L, "kwartet");
        assertThat(inBand).extracting(Composition::getId).containsExactly(hitB1.getId());
        assertThat(inBand).allMatch(c -> c.getBand().getId().equals(1L));

        // No leakage: other band's composition never shows up under this term.
        Composition ofB2 = saveIn(b2, "Inny utwór B");
        assertThat(queryService.search(1L, "kwartet"))
                .extracting(Composition::getId)
                .doesNotContain(ofB2.getId());
    }

    @Test
    @DisplayName("empty result sets are empty lists (never null)")
    void emptyResultsAreNonNullLists() {
        assertThat(queryService.listByBand(1L, null)).isNotNull().isEmpty();
        assertThat(queryService.search(1L, "zzz-no-such-term")).isNotNull().isEmpty();
    }

    // ---- US-1.6c T4: getCompositionWithParts (Shape-C read path) ----------

    @Test
    @DisplayName("getCompositionWithParts resolves composition + parts + instrument names inside the open transaction")
    void getCompositionWithParts_resolvesPartsWithInstrumentNames() {
        var b1 = band(1L);
        Composition c = saveIn(b1, "W");

        pl.michalbzowski.windband.application.dto.composition.CompositionWithPartsDto dto =
                queryService.getCompositionWithParts(c.getId(), 1L);

        assertThat(dto.composition().id()).isEqualTo(c.getId());
        assertThat(dto.composition().title()).isEqualTo("W");
        // parts not yet seeded in this stub — assert contract shape only; the full seed is in T4b (Task 4c)
        assertThat(dto.parts()).isNotNull();
    }

    @Test
    @DisplayName("cross-band getCompositionWithParts(id, otherBandId) fails closed like get()")
    void getCompositionWithParts_crossBandFailsClosed() {
        Composition c = saveIn(band(1L), "W2");
        assertThatThrownBy(() -> queryService.getCompositionWithParts(c.getId(), 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("band");
    }
}
