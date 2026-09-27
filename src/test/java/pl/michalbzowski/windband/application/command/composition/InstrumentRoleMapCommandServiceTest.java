package pl.michalbzowski.windband.application.command.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.dto.composition.InstrumentRoleMapDto;
import pl.michalbzowski.windband.application.query.composition.InstrumentRoleMapQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * US-7.3 — write side of the {@code instrument_role_map} admin surface, integrated against a real
 * database (PostgreSQL via Testcontainers in CI, H2 fallback locally). Every test rolls back via
 * the class-level transaction, so no manual cleanup is needed. Band 1 is the seeded default band;
 * each method that needs a second team creates its own isolated one.
 */
@Transactional
class InstrumentRoleMapCommandServiceTest extends BaseIntegrationTest {

    @Autowired
    private InstrumentRoleMapCommandService commandService;

    @Autowired
    private InstrumentRoleMapQueryService queryService;

    @Autowired
    private BandRepository bandRepository;

    private Band band2;

    private Band otherBand() {
        if (band2 == null) {
            String slug = "role-map-test-" + UUID.randomUUID().toString().substring(0, 8);
            band2 = bandRepository.save(Band.create("Role map test band", slug));
        }
        return band2;
    }

    private static String uniqueTag(String base) {
        return base + " " + System.nanoTime();
    }

    @Nested
    class AddMapping {

        @Test
        void persistsNewMappingForBand() {
            Long bandId = ensureDefaultBand().getId();
            String tag = uniqueTag("Klarinet");

            InstrumentRoleMap saved = commandService.addMapping(bandId, tag, "Klarynety 1", "na próbę");

            assertThat(saved.getId()).isNotNull();
            assertThat(queryService.listByBand(bandId)).anySatisfy(dto -> {
                assertThat(dto.sourceTag()).isEqualTo(tag);
                assertThat(dto.targetRolePattern()).isEqualTo("Klarynety 1");
                assertThat(dto.description()).isEqualTo("na próbę");
            });
        }

        @Test
        void blankDescriptionIsStoredAsNull() {
            Long bandId = ensureDefaultBand().getId();

            InstrumentRoleMap saved = commandService.addMapping(bandId, uniqueTag("Pikolina"), "Fagot", "   ");

            assertThat(saved.getDescription()).isNull();
        }

        @Test
        void rejectsDuplicateTagAndRole_caseInsensitively() {
            Long bandId = ensureDefaultBand().getId();
            String tag = uniqueTag("Baseta");

            commandService.addMapping(bandId, tag, "Tuba 1", null);

            assertThatThrownBy(() -> commandService.addMapping(bandId, tag.toUpperCase(), "Tuba 1", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("już istnieje");
        }

        @Test
        void allowsSameTagWithDifferentRole() {
            Long bandId = ensureDefaultBand().getId();
            String tag = uniqueTag("Klarnety");

            commandService.addMapping(bandId, tag, "Saksofon 1", null);

            // The unique contract is (band, lower(tag), exact role) — a second role on the same
            // tag is a legitimate different mapping and must succeed.
            InstrumentRoleMap second = commandService.addMapping(bandId, tag, "Saksofon 2", null);
            assertThat(second.getId()).isNotNull();
        }

        @Test
        void rejectsMappingForUnknownBand() {
            assertThatThrownBy(() -> commandService.addMapping(99_999L, "Flet", "Flet 1", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class DeleteMapping {

        @Test
        void removesOwnedRow() {
            Band band = ensureDefaultBand();
            String tag = uniqueTag("Alto");
            InstrumentRoleMap row = commandService.addMapping(band.getId(), tag, "Saksofon altowy", null);

            commandService.deleteMapping(band.getId(), row.getId());

            assertThat(queryService.listByBand(band.getId()))
                    .extracting(InstrumentRoleMapDto::sourceTag)
                    .doesNotContain(tag);
        }

        @Test
        void refusesToDeleteRowOwnedByAnotherBand() {
            Band foreign = otherBand();
            String tag = uniqueTag("Obce");
            InstrumentRoleMap row = commandService.addMapping(foreign.getId(), tag, "Róg 1", null);

            // Caller asks for the row inside band 1 — it is not there; fail closed, nothing deleted.
            assertThatThrownBy(() -> commandService.deleteMapping(ensureDefaultBand().getId(), row.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nie znaleziono mapowania");

            // The foreign row is untouched.
            assertThat(queryService.listByBand(foreign.getId()))
                    .extracting(InstrumentRoleMapDto::sourceTag)
                    .contains(tag);
        }

        @Test
        void unknownIdFailsClosedWithoutTouchingOtherRows() {
            Band band = ensureDefaultBand();
            String tag = uniqueTag("Zostań");
            commandService.addMapping(band.getId(), tag, "Perkusja 1", null);

            assertThatThrownBy(() -> commandService.deleteMapping(band.getId(), 987_654L))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(queryService.listByBand(band.getId()))
                    .extracting(InstrumentRoleMapDto::sourceTag)
                    .contains(tag);
        }
    }

    @Nested
    class ListByBand {

        @Test
        void isSortedByTagThenRole() {
            Band band = ensureDefaultBand();
            String suffix = String.valueOf(System.nanoTime());
            commandService.addMapping(band.getId(), "Zeta " + suffix, "Pozycja B", null);
            commandService.addMapping(band.getId(), "Alfa " + suffix, "Pozycja A", null);

            List<InstrumentRoleMapDto> rows = queryService.listByBand(band.getId()).stream()
                    .filter(dto -> dto.sourceTag().contains(suffix))
                    .toList();

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).sourceTag()).isEqualTo("Alfa " + suffix);
        }
    }

    private Band ensureDefaultBand() {
        return bandRepository.findById(1L).orElseGet(() -> {
            Band band = Band.create("Default Band", "default-band");
            band.update("Default Band", "default-band", "Band for testing");
            return bandRepository.save(band);
        });
    }

    @BeforeEach
    void resetSecondBand() {
        band2 = null; // one fresh team per test method (class-level rollback already dropped the previous)
    }
}
