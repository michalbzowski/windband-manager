package pl.michalbzowski.windband.application.query.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.dto.composition.ResolvedInstrumentRole;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-5.1 — alias-aware tag→role resolution integrated against a real database (PostgreSQL via
 * Testcontainers in CI, H2 fallback locally). Verifies the full read path: instrument rows with a
 * 1-step {@code aliasOf} hierarchy, seeded {@code instrument_role_map} rows via their port, and the
 * family-widening contract that the acceptance story pins ("Kornet" ⇒ Trąbka 1/2 and Kornet 1).
 *
 * <p>Each test rolls back through the class-level transaction; every fixture band is created fresh
 * per test (nanos suffix) so the V39 seed on band 1 never interferes with the unique index
 * {@code (band_id, lower(source_tag), target_role_pattern)}.
 */
@Transactional
class InstrumentRoleResolutionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private InstrumentRoleMapRepository roleMapRepository;

    @Autowired
    private InstrumentRoleResolutionQueryService resolutionService;

    private Band band;
    private Instrument trumbka; // root
    private Instrument kornet;  // alias → Trąbka
    private Instrument piccolo; // second alias → Trąbka

    @BeforeEach
    void seedAliasFamilyAndRoleMap() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        band = bandRepository.save(Band.create("ResIT " + suffix, "resit-" + suffix + "-" + System.nanoTime()));

        trumbka = instrumentRepository.save(Instrument.create("Trąbka", band));
        kornet = instrumentRepository.save(Instrument.create("Kornet", band));
        piccolo = instrumentRepository.save(Instrument.create("Piccolo Trąbkowy", band));

        kornet.setAliasOf(trumbka);
        instrumentRepository.save(kornet);
        piccolo.setAliasOf(trumbka);
        instrumentRepository.save(piccolo);

        roleMap(band, "Trąbka", "Trąbka 1");
        roleMap(band, "Trąbka", "Trąbka 2");
        roleMap(band, "Kornet", "Kornet 1");
    }

    private InstrumentRoleMap roleMap(Band b, String tag, String role) {
        return roleMapRepository.save(InstrumentRoleMap.forBand(b, tag, role, null));
    }

    @Nested
    class AcceptanceExample {
        @Test
        void kornet_player_covers_all_three_of_the_storys_roles() {
            List<String> roles = resolutionService.resolveRoleNames(band.getId(), "Kornet");

            // Story: "\"Kornet\" (alias of \"Trąbka\") can legitimately match \"Trąbka 1\", \"Trąbka 2\", and \"Kornet 1\""
            assertThat(roles).containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2", "Kornet 1");
        }

        @Test
        void root_player_gets_the_same_family_roles_symmetry() {
            List<String> roles = resolutionService.resolveRoleNames(band.getId(), "Trąbka");

            assertThat(roles).containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2", "Kornet 1");
        }

        @Test
        void second_alias_of_same_root_also_covers_the_family() {
            List<String> roles = resolutionService.resolveRoleNames(band.getId(), "Piccolo Trąbkowy");

            assertThat(roles).containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2", "Kornet 1");
        }

        @Test
        void matched_tag_provenance_is_exposed_for_each_role() {
            List<ResolvedInstrumentRole> roles = resolutionService.resolveRoles(band.getId(), "Kornet");

            assertThat(roles).anySatisfy(r -> {
                assertThat(r.roleLabel()).isEqualTo("Trąbka 1");
                assertThat(r.matchedSourceTag()).isEqualTo("Trąbka");
            }).anySatisfy(r -> {
                assertThat(r.roleLabel()).isEqualTo("Kornet 1");
                assertThat(r.matchedSourceTag()).isEqualTo("Kornet");
            });
        }
    }

    @Nested
    class EdgeCases {
        @Test
        void unknown_tag_with_a_hand_written_row_still_resolves() {
            roleMap(band, "Basowka", "Tuba Bb 1");

            List<String> roles = resolutionService.resolveRoleNames(band.getId(), "Basowka");

            assertThat(roles).containsExactly("Tuba Bb 1");
        }

        @Test
        void blank_input_returns_empty_and_leaves_the_role_map_untouched() {
            assertThat(resolutionService.resolveRoleNames(band.getId(), "")).isEmpty();
            assertThat(resolutionService.resolveRoleNames(band.getId(), "   ")).isEmpty();
            assertThat(resolutionService.resolveRoleNames(band.getId(), null)).isEmpty();

            assertThat(roleMapRepository.findAllByBandId(band.getId())).hasSize(3); // seed intact
        }

        @Test
        void unknown_band_fails_closed_with_illegal_argument() {
            assertThatThrownBy(() -> resolutionService.resolveRoles(987_654L, "Kornet"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unrelated_roots_stay_outside_the_family() {
            Instrument waltornia = instrumentRepository.save(Instrument.create("Waltornia", band));
            roleMap(band, "Waltornia", "Horn 1");

            List<String> kornetRoles = resolutionService.resolveRoleNames(band.getId(), "Kornet");

            assertThat(kornetRoles)
                    .containsExactlyInAnyOrder("Trąbka 1", "Trąbka 2", "Kornet 1")
                    .doesNotContain("Horn 1");
        }
    }

    @Nested
    class BandIsolation {
        @Test
        void another_bands_alias_family_is_never_mixed_in() {
            String suffix = "x" + UUID.randomUUID().toString().substring(0, 8);
            Band other = bandRepository.save(Band.create("ResIT other " + suffix, "resit-other-" + suffix));

            Instrument otherTrumbka = instrumentRepository.save(Instrument.create("Trąbka", other));
            Instrument otherKornet = instrumentRepository.save(Instrument.create("Fagotti", other));
            otherKornet.setAliasOf(otherTrumbka);
            instrumentRepository.save(otherKornet);

            roleMap(other, "Fagotti", "Róg basowy 1");

            assertThat(resolutionService.resolveRoleNames(band.getId(), "Kornet"))
                    .doesNotContain("Róg basowy 1");
            // And the neighbour stays resolvable on its own side.
            assertThat(resolutionService.resolveRoleNames(other.getId(), "Fagotti"))
                    .containsExactlyInAnyOrder("Róg basowy 1");
        }
    }
}
