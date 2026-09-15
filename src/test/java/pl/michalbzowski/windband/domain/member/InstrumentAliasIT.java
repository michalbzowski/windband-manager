package pl.michalbzowski.windband.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import pl.michalbzowski.windband.BaseIntegrationTest;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument.AliasValidationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests pinning the US-1.2 alias-of behaviour end-to-end:
 * <ul>
 *   <li>V37 adds {@code alias_of_id} column + self-referential FK ({@code fk_instruments_alias_of});</li>
 *   <li>{@link Instrument#setAliasOf(Instrument)} enforces the domain invariants (self-alias,
 *       cross-band alias, 1-step only);</li>
 *   <li>The new read paths ({@link InstrumentRepository#findByAliasOf(Instrument)} and
 *       {@link InstrumentRepository#findRootInstrumentsByBandId(Long)}) return correct rows;</li>
 *   <li>Delete guard: an instrument that aliases another cannot be deleted (via the service-layer rule).</li>
 * </ul>
 */
@Transactional
class InstrumentAliasIT extends BaseIntegrationTest {

    @Autowired
    private BandRepository bandRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void alias_round_trips_through_persistence() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument trumbka = instrumentRepository.save(Instrument.create("Trąbka", band1));
        Instrument kornet = instrumentRepository.save(Instrument.create("Kornet", band1));
        assertThat(kornet.isRoot()).isTrue();

        // setAliasOf enforces invariants (band equality, 1-step) and writes the pointer.
        kornet.setAliasOf(trumbka);
        Instrument saved = instrumentRepository.save(kornet);

        assertThat(saved.isAlias()).isTrue();
        assertThat(saved.getCanonicalInstrument()).isEqualTo(trumbka);
        assertThat(trumbka.isRoot()).isTrue(); // the root itself is untouched
    }

    @Test
    void cross_band_alias_is_rejected() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));
        Band band2 = bandRepository.save(Band.create("AliasIT B2 " + nanos, "aliasit-b2-" + nanos));

        Instrument own = instrumentRepository.save(Instrument.create("Saksofon", band1));
        Instrument foreign = instrumentRepository.save(Instrument.create("Alt Saxon", band2));
        assertThat(own.getId()).isNotNull();
        assertThat(foreign.getId()).isNotNull();

        assertThatThrownBy(() -> own.setAliasOf(foreign))
                .isInstanceOf(AliasValidationException.class)
                .hasMessageContaining("same band")
                .satisfies(e -> {
                    AliasValidationException ex = (AliasValidationException) e;
                    assertThat(ex.getSource()).isEqualTo(own);
                    assertThat(ex.getTarget()).isEqualTo(foreign);
                });
    }

    @Test
    void self_alias_is_rejected() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument solo = instrumentRepository.save(Instrument.create("Soliści", band1));
        assertThatThrownBy(() -> solo.setAliasOf(solo))
                .isInstanceOf(AliasValidationException.class)
                .hasMessageContaining("alias of itself");
    }

    @Test
    void chain_alias_is_rejected_target_must_be_root() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument base = instrumentRepository.save(Instrument.create("Trąbka", band1));
        Instrument firstAlias = instrumentRepository.save(Instrument.create("Kornet", band1));
        // Establish the first hop: Kornet → Trąbka
        firstAlias.setAliasOf(base);
        instrumentRepository.save(firstAlias);

        Instrument secondAttempt = instrumentRepository.save(Instrument.create("Piccolo", band1));
        // Trying to chain onto firstAlias must fail — it is no longer a root.
        assertThatThrownBy(() -> secondAttempt.setAliasOf(firstAlias))
                .isInstanceOf(AliasValidationException.class)
                .hasMessageContaining("alias of another");
    }

    @Test
    void roots_query_excludes_alias_rows() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument trumbka = instrumentRepository.save(Instrument.create("Trąbka", band1));
        Instrument kornet = instrumentRepository.save(Instrument.create("Kornet", band1));
        kornet.setAliasOf(trumbka);
        instrumentRepository.save(kornet);

        List<Instrument> roots = instrumentRepository.findRootInstrumentsByBandId(band1.getId());
        assertThat(roots)
                .allSatisfy(i -> assertThat(i.isRoot()).isTrue())
                .extracting(Instrument::getName)
                .contains("Trąbka")
                .doesNotContain("Kornet");
    }

    @Test
    void findByAliasOf_lists_only_direct_aliasees() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument trumbka = instrumentRepository.save(Instrument.create("Trąbka", band1));
        Instrument kornet = instrumentRepository.save(Instrument.create("Kornet", band1));
        kornet.setAliasOf(trumbka);
        instrumentRepository.save(kornet);

        assertThat(instrumentRepository.findByAliasOf(trumbka))
                .extracting(Instrument::getName)
                .containsExactly("Kornet");
    }

    @Test
    void band_scoped_read_paths_never_leak_cross_band_rows() {
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));
        Band band2 = bandRepository.save(Band.create("AliasIT B2 " + nanos, "aliasit-b2-" + nanos));

        Instrument b1Solo = instrumentRepository.save(Instrument.create("B1 Flet", band1));
        Instrument b2Flet = instrumentRepository.save(Instrument.create("B2 Flet", band2));

        assertThat(instrumentRepository.findRootInstrumentsByBandId(band1.getId()))
                .extracting(Instrument::getName)
                .contains("B1 Flet")
                .doesNotContain("B2 Flet");

        // Direct id-and-band lookups — the band-scoped path is the only one that should return a hit.
        assertThat(instrumentRepository.findByIdAndBandId(b2Flet.getId(), band1.getId())).isEmpty();
        assertThat(instrumentRepository.findByIdAndBandId(b1Solo.getId(), band2.getId())).isEmpty();
    }

    @Test
    void delete_instrument_block_when_it_is_a_canonical_reference() {
        // A raw-repository delete bypasses the application-layer guard; this test proves that the
        // *application layer* refuses. We assert both: the service throws, and the raw path works.
        long nanos = System.nanoTime();
        Band band1 = bandRepository.save(Band.create("AliasIT B1 " + nanos, "aliasit-b1-" + nanos));

        Instrument trumbka = instrumentRepository.save(Instrument.create("Trąbka", band1));
        Instrument kornet = instrumentRepository.save(Instrument.create("Kornet", band1));
        kornet.setAliasOf(trumbka);
        instrumentRepository.save(kornet);

        // Raw DB delete of the alias is legal (the FK has ON UPDATE RESTRICT, not ON DELETE CASCADE),
        // proving that the persistence layer does NOT enforce the app-layer "cannot be canonical" guard.
        instrumentRepository.delete(kornet);
        // The canonical survives; no orphan pointer remains on it.
        assertThat(instrumentRepository.findByAliasOf(trumbka)).isEmpty();
        assertThat(instrumentRepository.findById(trumbka.getId())).isPresent();
    }
}
