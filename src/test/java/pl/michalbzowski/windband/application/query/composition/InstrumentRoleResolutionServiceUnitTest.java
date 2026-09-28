package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.dto.composition.ResolvedInstrumentRole;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit contract for {@link InstrumentRoleResolutionQueryService} (US-5.1) against mocked ports —
 * no Spring context, no DB. Pins the resolution semantics: alias-family widening, symmetry between a
 * root tag and its aliases, manual-row resolution without an instrument row, case folding, role dedup,
 * deterministic ordering, fail-closed band guard, and the flat name projection US-5.3 consumes.
 */
@ExtendWith(MockitoExtension.class)
class InstrumentRoleResolutionServiceUnitTest {

    private static final long BAND_ID = 1L;

    @Mock
    private BandQueryService bandQueryService;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private InstrumentRoleMapRepository roleMapRepository;

    @InjectMocks
    private InstrumentRoleResolutionQueryService service;

    private Band band;

    @BeforeEach
    void setUp() {
        band = Band.create("Test Band", "test-band-" + System.nanoTime());
        setBandId(band, BAND_ID); // InstrumentRoleMap.forBand requires a persisted-style band id
    }

    @Test
    void alias_tag_resolves_to_its_own_roles_and_the_canonical_roots_roles() {
        // Band: Trąbka (root) ← Kornet (alias). Role map typed under both names.
        Instrument trumbka = instrument("Trąbka", null);
        Instrument kornet = instrument("Kornet", trumbka);
        stubBandInstruments(trumbka, kornet);
        stubRoleRows(row(band, "Trąbka", "Trąbka 1"),
                row(band, "Trąbka", "Trąbka 2"),
                row(band, "Kornet", "Kornet 1"));

        List<ResolvedInstrumentRole> roles = service.resolveRoles(BAND_ID, "Kornet");

        // The US-5.1 acceptance example: "Kornet" matches "Trąbka 1", "Trąbka 2" and "Kornet 1".
        assertThat(roles).extracting(ResolvedInstrumentRole::roleLabel)
                .containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2", "Kornet 1");
    }

    @Test
    void root_tag_resolves_to_its_aliases_roles_too_symmetry_holds() {
        Instrument trumbka = instrument("Trąbka", null);
        Instrument kornet = instrument("Kornet", trumbka);
        Instrument piccolo = instrument("Piccolo Trąbkowy", trumbka);
        stubBandInstruments(trumbka, kornet, piccolo);
        stubRoleRows(row(band, "Kornet", "Kornet 1"),
                row(band, "Trąbka", "Trąbka 1"),
                row(band, "Piccolo Trąbkowy", "Tuba Piccolo"));

        assertThat(service.resolveRoles(BAND_ID, "Trąbka")).extracting(ResolvedInstrumentRole::roleLabel)
                .containsExactlyInAnyOrder("Trąbka 1", "Kornet 1", "Tuba Piccolo");
    }

    @Test
    void unknown_tag_with_hand_written_role_rows_still_resolves() {
        stubBandInstruments(instrument("Flet", null));
        // No "Piccolo" instrument exists, but the manager typed a role-map row for the tag anyway.
        stubRoleRows(row(band, "Piccolo", "Piccolo 1"));

        List<ResolvedInstrumentRole> roles = service.resolveRoles(BAND_ID, "Piccolo");

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).roleLabel()).isEqualTo("Piccolo 1");
        assertThat(roles.get(0).matchedSourceTag()).isEqualTo("Piccolo");
    }

    @Test
    void duplicate_roles_from_differently_cased_tags_are_collapsed() {
        stubBandInstruments(instrument("Trąbka", null));
        stubRoleRows(row(band, "trąbka", "Trąbka 1"), row(band, "TRĄBKA", "trąbka 1"));

        List<ResolvedInstrumentRole> roles = service.resolveRoles(BAND_ID, "Trąbka");

        assertThat(roles).hasSize(1);
        assertThat(roles.get(0).roleLabel()).isEqualTo("Trąbka 1"); // first writer's spelling survives
    }

    @Test
    void tags_outside_the_alias_family_do_not_match() {
        Instrument trumbka = instrument("Trąbka", null);
        Instrument kornet = instrument("Kornet", trumbka);
        Instrument waltornia = instrument("Waltornia", null);
        stubBandInstruments(trumbka, kornet, waltornia);
        stubRoleRows(row(band, "Trąbka", "Trąbka 1"), row(band, "Waltornia", "Waltornia 1"));

        assertThat(service.resolveRoles(BAND_ID, "Kornet")).extracting(ResolvedInstrumentRole::roleLabel)
                .containsExactlyInAnyOrder("Trąbka 1")
                .doesNotContain("Waltornia 1");
    }

    @Test
    void case_insensitive_input_tag_matches_casually_typed_rows() {
        Instrument trumbka = instrument("Trąbka", null);
        Instrument kornet = instrument("Kornet", trumbka);
        stubBandInstruments(trumbka, kornet);
        stubRoleRows(row(band, "Trąbka", "Trąbka 1"));

        assertThat(service.resolveRoles(BAND_ID, "kOrNeT")).extracting(ResolvedInstrumentRole::roleLabel)
                .containsExactly("Trąbka 1");
    }

    @Test
    void results_are_sorted_by_role_label_regardless_of_row_order() {
        Instrument trumbka = instrument("Trąbka", null);
        stubBandInstruments(trumbka);
        // Wszystkie wiersze pod jednym tagiem — test czysto kolejnościowy (DB zwraca je przypadkowo).
        stubRoleRows(row(band, "Trąbka", "Trąbka 2"), row(band, "Trąbka", "Kornet 9"),
                row(band, "Trąbka", "Trąbka 1"));

        assertThat(service.resolveRoles(BAND_ID, "Trąbka")).extracting(ResolvedInstrumentRole::roleLabel)
                .containsExactly("Kornet 9", "Trąbka 1", "Trąbka 2");
    }

    @Test
    void blank_and_null_tag_return_empty_without_touching_any_port() {
        assertThat(service.resolveRoles(BAND_ID, "")).isEmpty();
        assertThat(service.resolveRoles(BAND_ID, "   ")).isEmpty();
        assertThat(service.resolveRoles(BAND_ID, null)).isEmpty();

        verify(bandQueryService, never()).getRequiredBand(BAND_ID);
    }

    @Test
    void unknown_band_fails_closed_before_any_repository_read() {
        when(bandQueryService.getRequiredBand(999L))
                .thenThrow(new IllegalArgumentException("no such band: 999"));

        assertThatThrownBy(() -> service.resolveRoles(999L, "Trąbka"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
        verify(instrumentRepository, never()).findAllOrderBySortPriorityByBandId(999L);
        verify(roleMapRepository, never()).findAllByBandId(999L);
    }

    @Test
    void resolve_role_names_projects_the_flat_list_for_distribution() {
        Instrument trumbka = instrument("Trąbka", null);
        Instrument kornet = instrument("Kornet", trumbka);
        stubBandInstruments(trumbka, kornet);
        stubRoleRows(row(band, "Trąbka", "Trąbka 1"), row(band, "Kornet", "Kornet 1"));

        List<String> names = service.resolveRoleNames(BAND_ID, "Kornet");

        assertThat(names).containsExactlyInAnyOrder("Trąbka 1", "Kornet 1");
    }

    // ─────────────────────────── test factories / stubs ───────────────────────────

    private Instrument instrument(String name, Instrument aliasOf) {
        Instrument created = Instrument.create(name, band);
        if (aliasOf != null) {
            created.setAliasOf(aliasOf);
        }
        return created;
    }

    private InstrumentRoleMap row(Band b, String sourceTag, String role) {
        return InstrumentRoleMap.forBand(b, sourceTag, role, null);
    }

    private void stubBandInstruments(Instrument... instruments) {
        when(instrumentRepository.findAllOrderBySortPriorityByBandId(BAND_ID))
                .thenReturn(List.of(instruments));
    }

    private void stubRoleRows(InstrumentRoleMap... rows) {
        when(roleMapRepository.findAllByBandId(BAND_ID)).thenReturn(List.of(rows));
    }

    private static void setBandId(Band b, Long id) {
        try {
            var field = Band.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(b, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
