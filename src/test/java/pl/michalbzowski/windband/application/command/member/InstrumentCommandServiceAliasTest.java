package pl.michalbzowski.windband.application.command.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.Instrument.AliasValidationException;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Unit tests for the US-1.2 alias write-path in {@link InstrumentCommandService} (Mockito only).
 * The domain invariants are covered by {@code InstrumentAliasIT}; here we pin the SERVICE-side
 * guards: band scoping on both operands, root-only target rule, self-alias rejection, the
 * alias-list filtering by band, and the delete guard when an entity is aliased by another row.
 */
@ExtendWith(MockitoExtension.class)
class InstrumentCommandServiceAliasTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private BandRepository bandRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private InstrumentCommandService service;

    private Band bandA;
    private Band bandB;
    private Instrument rootInBandA;
    private Instrument rootInBandB;
    private Instrument aliasInBandA;

    private static final Long BAND_A_ID = 100L;
    private static final Long BAND_B_ID = 200L;
    private static final Long ROOT_A_ID = 10L;
    private static final Long ROOT_B_ID = 11L;
    private static final Long ALIAS_A_ID = 12L;

    @BeforeEach
    void fixtures() {
        bandA = Band.create("A", "a");
        bandB = Band.create("B", "b");
        rootInBandA = Instrument.create("Trąbka A", bandA);
        rootInBandB = Instrument.create("Trąbka B", bandB);
        aliasInBandA = Instrument.create("Kornet A", bandA);

        setBandId(bandA, BAND_A_ID);
        setBandId(bandB, BAND_B_ID);
        setInstrumentId(rootInBandA, ROOT_A_ID);
        setInstrumentId(rootInBandB, ROOT_B_ID);
        setInstrumentId(aliasInBandA, ALIAS_A_ID);
    }

    @Test
    void update_alias_of_persists_and_returns_the_saved_row() {
        when(instrumentRepository.findById(ALIAS_A_ID)).thenReturn(Optional.of(aliasInBandA));
        when(instrumentRepository.findById(ROOT_A_ID)).thenReturn(Optional.of(rootInBandA));
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        Instrument result = service.updateAliasOf(ALIAS_A_ID, ROOT_A_ID, BAND_A_ID);

        assertThat(result.isAlias()).isTrue();
        assertThat(result.getCanonicalInstrument()).isEqualTo(rootInBandA);
        verify(instrumentRepository).save(aliasInBandA);
    }

    @Test
    void update_alias_of_rejects_self_alias() {
        assertThatThrownBy(() -> service.updateAliasOf(ALIAS_A_ID, ALIAS_A_ID, BAND_A_ID))
                .isInstanceOf(AliasValidationException.class)
                .hasMessageContaining("alias of itself");
        // The self-alias check fires before any repository lookup.
        verify(instrumentRepository, never()).findById(ALIAS_A_ID);
        verify(instrumentRepository, never()).save(any(Instrument.class));
    }

    @Test
    void update_alias_of_requires_the_target_to_be_a_root() {
        // Root A is itself an alias of another row (a grand-root) — the "1-step only" rule kicks in.
        Instrument grandRoot = Instrument.create("Grand", bandA);
        setInstrumentId(grandRoot, 90L);
        rootInBandA.setAliasOf(grandRoot); // now rootInBandA is no longer a root
        when(instrumentRepository.findById(ALIAS_A_ID)).thenReturn(Optional.of(aliasInBandA));
        when(instrumentRepository.findById(ROOT_A_ID)).thenReturn(Optional.of(rootInBandA));

        assertThatThrownBy(() -> service.updateAliasOf(ALIAS_A_ID, ROOT_A_ID, BAND_A_ID))
                .isInstanceOf(AliasValidationException.class)
                .hasMessageContaining("Trąbka A")  // name of the offending target
                .satisfies(e -> {
                    AliasValidationException ex = (AliasValidationException) e;
                    assertThat(ex.getTarget()).isEqualTo(rootInBandA);
                });
        verify(instrumentRepository, never()).save(any(Instrument.class));
    }

    @Test
    void update_alias_of_rejects_cross_band_aliases() {
        when(instrumentRepository.findById(ALIAS_A_ID)).thenReturn(Optional.of(aliasInBandA));
        // rootInBandB belongs to band B, but the caller is in band A — band scoping fires first.
        when(instrumentRepository.findById(ROOT_B_ID)).thenReturn(Optional.of(rootInBandB));

        assertThatThrownBy(() -> service.updateAliasOf(ALIAS_A_ID, ROOT_B_ID, BAND_A_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Instrument not found: " + ROOT_B_ID);
        verify(instrumentRepository, never()).save(any(Instrument.class));
    }

    @Test
    void update_alias_of_surfaces_not_found_when_target_id_is_unknown() {
        when(instrumentRepository.findById(ALIAS_A_ID)).thenReturn(Optional.of(aliasInBandA));
        when(instrumentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAliasOf(ALIAS_A_ID, 99L, BAND_A_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Instrument not found: 99");
        verify(instrumentRepository, never()).save(any(Instrument.class));
    }

    @Test
    void get_aliases_filters_out_rows_from_other_bands() {
        when(instrumentRepository.findById(ROOT_A_ID)).thenReturn(Optional.of(rootInBandA));
        Instrument stray = Instrument.create("Stray", bandB);
        setInstrumentId(stray, 20L);
        when(instrumentRepository.findByAliasOf(rootInBandA)).thenReturn(List.of(aliasInBandA, stray));

        List<Instrument> filtered = service.getAliases(ROOT_A_ID, BAND_A_ID);

        // stray (band B) is excluded by the band filter; aliasInBandA (band A) stays.
        assertThat(filtered).containsExactly(aliasInBandA);
    }

    @Test
    void delete_instrument_blocks_when_another_instrument_aliasees_it() {
        when(instrumentRepository.findById(ROOT_A_ID)).thenReturn(Optional.of(rootInBandA));
        when(memberRepository.findByInstrument(rootInBandA)).thenReturn(List.of());
        when(instrumentRepository.findByAliasOf(rootInBandA)).thenReturn(List.of(aliasInBandA));

        InstrumentCommandService.InstrumentInUseException caught = null;
        try {
            service.deleteInstrument(ROOT_A_ID, BAND_A_ID);
        } catch (InstrumentCommandService.InstrumentInUseException e) {
            caught = e;
        }
        assertThat(caught).isNotNull();
        // The exception exposes the canonical instrument name for programmatic UI rendering.
        assertThat(caught.getInstrumentName()).isEqualTo("Trąbka A");
        verify(instrumentRepository, never()).delete(any(Instrument.class));
    }

    @Test
    void clear_alias_removes_the_pointer() {
        when(instrumentRepository.findById(ALIAS_A_ID)).thenReturn(Optional.of(aliasInBandA));
        aliasInBandA.setAliasOf(rootInBandA); // pre-condition: currently an alias
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        Instrument cleared = service.clearAlias(ALIAS_A_ID, BAND_A_ID);

        assertThat(cleared.isRoot()).isTrue();
        verify(instrumentRepository).save(aliasInBandA);
    }

    // ── Reflection helpers (factories do not assign ids) ─────────────────────
    private static void setInstrumentId(Instrument instrument, Long id) {
        try {
            var f = Instrument.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(instrument, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setBandId(Band band, Long id) {
        try {
            var f = Band.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(band, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
