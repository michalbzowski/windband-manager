package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.application.query.instrument.InstrumentQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for issue: tags (instruments) leaking across teams.
 *
 * <p>Symptoms before this fix:
 * <ul>
 *   <li>A user creating a brand-new team would see the 13 instruments seeded in V13
 *       (Trąbka, Flet, Oboj, Klarnet, Fagot, Saksofon Alt/Tenor/Bariton, Róg, Puzon,
 *       Tuba, Perkusja, Dyrygent) — none of which they had created — simply because the
 *       seed predated multi-tenant support and the V23 backfill could not assign a band
 *       to instruments that were never used by anyone.</li>
 *   <li>Root cause: {@code SpringDataInstrumentRepository.findAllByBandIdOrderBySortPriorityAsc}
 *       queried {@code WHERE i.band.id = :bandId OR i.band IS NULL}, intentionally
 *       treating legacy NULL-band rows as a "global catalog" visible to every team.</li>
 * </ul>
 *
 * <p>Fix applied in this commit:
 * <ol>
 *   <li>V28 migration: every NULL-band row gets {@code band_id = 1} (the default seed band),
 *       then {@code band_id} is set to {@code NOT NULL}.</li>
 *   <li>Repository query dropped the {@code OR i.band IS NULL} clause.</li>
 *   <li>JPA entity {@code Instrument} marks {@code band} as {@code nullable = false} so the
 *       test schema (H2 created by Hibernate) matches the production schema.</li>
 * </ol>
 *
 * <p>What this test asserts:
 * <ol>
 *   <li>Instruments created in band 1 are visible to band 1.</li>
 *   <li>The SAME instruments are NOT visible to band 2 (no leakage).</li>
 *   <li>Creating a new instrument in band 2 does not leak it to band 1.</li>
 *   <li>Looking up an instrument from band 2 by ID that belongs to band 1 throws.</li>
 * </ol>
 */
@Transactional
class InstrumentTeamIsolationTest extends BaseIntegrationTest {

    @Autowired
    private InstrumentCommandService commandService;

    @Autowired
    private InstrumentQueryService queryService;

    @Autowired
    private BandRepository bandRepository;

    private Long band1Id;
    private Long band2Id;

    @BeforeEach
    void setUp() {
        band1Id = 1L;
        Band band2 = bandRepository.save(Band.create("Drugi Zespół " + System.nanoTime(), "drugi-zespol-" + System.nanoTime()));
        band2Id = band2.getId();
    }

    @Test
    void band2ShouldNotSeeInstrumentsCreatedInBand1() {
        // Given: an instrument created in band 1 (with a unique name so we can find it)
        String band1OnlyName = "Band1Instrument-" + System.nanoTime();
        commandService.createInstrument(band1OnlyName, "Only in band 1", 99, band1Id);

        // When: band 2 lists its instruments
        List<Instrument> band2Instruments = queryService.findAll(band2Id);

        // Then: the band-1 instrument is not visible
        assertThat(band2Instruments)
                .extracting(Instrument::getName)
                .doesNotContain(band1OnlyName);
    }

    @Test
    void band1ShouldNotSeeInstrumentsCreatedInBand2() {
        // Given: an instrument created in band 2
        String band2OnlyName = "Band2Instrument-" + System.nanoTime();
        commandService.createInstrument(band2OnlyName, "Only in band 2", 99, band2Id);

        // When: band 1 lists its instruments (note: band 1 already has seed instruments
        // from data.sql — Trąbka, Bęben, Saksofon — but none named like the band-2 one)
        List<Instrument> band1Instruments = queryService.findAll(band1Id);

        // Then: the band-2 instrument is not visible
        assertThat(band1Instruments)
                .extracting(Instrument::getName)
                .doesNotContain(band2OnlyName);
    }

    @Test
    void instrumentScopedToBand1ShouldThrowWhenFetchedByBand2() {
        // Given: an instrument in band 1
        String name = "ScopedInstr-" + System.nanoTime();
        Instrument instrument = commandService.createInstrument(name, "Scoped to band 1", 50, band1Id);

        // When/Then: band 2 tries to fetch this instrument by ID
        assertThatThrownBy(() -> commandService.getInstrumentById(instrument.getId(), band2Id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Instrument not found");
    }

    @Test
    void newTeamSeesNoSeedInstrumentsWhenBandHasNone() {
        // Given: a brand-new band 2 that has not created any instruments
        // When: band 2 lists its instruments
        List<Instrument> band2Instruments = queryService.findAll(band2Id);

        // Then: the list is empty — no legacy/global instruments leak in
        assertThat(band2Instruments)
                .as("New team should see only instruments its members created")
                .isEmpty();
    }

    @Test
    void queryByBandIdExcludesInstrumentsFromOtherBands() {
        // Given: instruments created in band 1 and band 2 with unique names
        String band1Name = "Team1Only-" + System.nanoTime();
        String band2Name = "Team2Only-" + System.nanoTime();
        commandService.createInstrument(band1Name, "only team 1", 99, band1Id);
        commandService.createInstrument(band2Name, "only team 2", 99, band2Id);

        // When: query band-scoped instruments for each team
        List<Instrument> band1Instruments = queryService.findAll(band1Id);
        List<Instrument> band2Instruments = queryService.findAll(band2Id);

        // Then: each team sees only its own instruments
        assertThat(band1Instruments).extracting(Instrument::getName).contains(band1Name);
        assertThat(band1Instruments).extracting(Instrument::getName).doesNotContain(band2Name);

        assertThat(band2Instruments).extracting(Instrument::getName).contains(band2Name);
        assertThat(band2Instruments).extracting(Instrument::getName).doesNotContain(band1Name);
    }
}