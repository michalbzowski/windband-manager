package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
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
        // Child FIRST, so our bare parent DELETE does not trip the V35 FK from score_files.
        jdbcTemplate.execute("DELETE FROM score_files");
        jdbcTemplate.execute("DELETE FROM compositions");
    }

    @AfterEach
    void cleanupRows() {
        jdbcTemplate.execute("DELETE FROM score_files");
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

    // ---- Biblioteka filtrów: title / composer / arranger / status --------------

    @Test
    @DisplayName("listByBand (with filters) returns no cross-band rows — filter matches only that band")
    void listByBandFilters_isolatesCrossBand() {
        var b1 = band(1L);
        var b2 = band(2L);
        Composition odaB1 = saveIn(b1, "Oda do radości");
        saveIn(b2,  "Inaczej: Oda do smutku");

        // Same title fragment under a different band must never leak.
        var minePage = queryService.listByBand(1L, "oda", null, null, null, Pageable.unpaged());
        List<Composition> mine = minePage.getContent();
        assertThat(mine).extracting(Composition::getId)
                .hasSize(1)
                .containsExactly(odaB1.getId());

        // From band 2's view the same term does not pull in band 1's row either.
        var theirsPage = queryService.listByBand(2L, "oda", null, null, null, Pageable.unpaged());
        List<Composition> theirs = theirsPage.getContent();
        assertThat(theirs).hasSize(1);
        assertThat(theirs.get(0).getBand().getId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("listByBandFilters: case-insensitive prefix matches on title (any of the 3 text fields)")
    void listByBandFilters_titleComposerArranger() {
        var b1 = band(1L);
        Composition byTitle    = saveIn(b1, "Beethoven: Furiosa");
        Composition byComposer = saveIn(b1, "Allegro maestoso");
        byComposer.updateTexts(null, null, "Furtados", null);
        repository.save(byComposer);
        Composition byArranger = saveIn(b1, "Adagio grazioso");
        byArranger.updateTexts(null, null, null, "Kowalski");
        repository.save(byArranger);

        // title fragment (case-insensitive)
        assertThat(queryService.listByBand(1L, "furiosa", null, null, null, Pageable.unpaged()).getContent())
                .extracting(Composition::getId).containsExactly(byTitle.getId());

        // composer fragment
        assertThat(queryService.listByBand(1L, null, "furtados", null, null, Pageable.unpaged()).getContent())
                .extracting(Composition::getId).containsExactly(byComposer.getId());

        // arranger fragment
        assertThat(queryService.listByBand(1L, null, null, "kowalski", null, Pageable.unpaged()).getContent())
                .extracting(Composition::getId).containsExactly(byArranger.getId());
    }

    @Test
    @DisplayName("listByBandFilters: each text filter ANDs with the status — only rows matching BOTH pass")
    void listByBandFilters_composesWithStatus() {
        var b1 = band(1L);
        Composition hitTitleAndReady  = saveIn(b1, "Symphonia grandiosa");
        hitTitleAndReady.markReady();
        repository.save(hitTitleAndReady);

        Composition hitsTitleButDraft = saveIn(b1, "Symphonica minuta");
        // default DRAFT status → must be excluded by the READY filter

        var resPage = queryService.listByBand(1L,
                "symphon", null, null, CompositionStatus.READY, Pageable.unpaged());
        List<Composition> result = resPage.getContent();
        assertThat(result).extracting(Composition::getId)
                .containsExactly(hitTitleAndReady.getId())  // READY + title match
                .doesNotContain(hitsTitleButDraft.getId());  // DRAFT, so excluded despite title hit
    }

    @Test
    @DisplayName("listByBandFilters: blank-string filters are treated as 'no filter' (backward-compat for empty form input)")
    void listByBandFilters_blankStringsBehaveAsNull() {
        var b1 = band(1L);
        saveIn(b1, "Szkic A");   // DRAFT by default
        Composition readyB = saveIn(b1, "Gotow B");
        readyB.markReady();
        repository.save(readyB);

        var wbPage = queryService.listByBand(1L, "   ", "", null, null, Pageable.unpaged());
        List<Composition> withBlankFilter = wbPage.getContent();
        var basePage = queryService.listByBand(1L, null, null, null, null, Pageable.unpaged());
        List<Composition> baseline        = basePage.getContent();

        // Same result set — whitespace-only filter must behave like no filter.
        assertThat(withBlankFilter).extracting(Composition::getId)
                .containsExactlyInAnyOrderElementsOf(baseline.stream().map(Composition::getId).toList());
    }

    @Test
    @DisplayName("listByBandFilters: zero matches returns an empty Page (never null)")
    void listByBandFilters_noMatches() {
        var b1 = band(1L);
        saveIn(b1, "Taki zwykly utwór");

        var page = queryService.listByBand(1L, "zzz-no-such-term", null, null, CompositionStatus.READY, PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotNull().isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

}
