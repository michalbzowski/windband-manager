package pl.michalbzowski.windband.application.query.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMap;
import pl.michalbzowski.windband.domain.composition.InstrumentRoleMapRepository;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;

/**
 * Faza D — unit contract of AI role → band instrument/member resolution
 * (exact name OR an instrument_role_map row; root beats alias; diacritics-blind).
 */
class RoleInstrumentResolverTest {

    private final InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final InstrumentRoleMapRepository roleMapRepository = mock(InstrumentRoleMapRepository.class);
    private final RoleInstrumentResolver resolver =
            new RoleInstrumentResolver(instrumentRepository, memberRepository, roleMapRepository);

    // --- fixtures -----------------------------------------------------------

    private static Instrument instrument(long id, String name, Instrument aliasOf) {
        Instrument i = mock(Instrument.class);
        when(i.getId()).thenReturn(id);
        when(i.getName()).thenReturn(name);
        when(i.getAliasOf()).thenReturn(aliasOf);
        return i;
    }

    private static Member member(String first, String last, Instrument... instruments) {
        Member m = mock(Member.class);
        when(m.getFirstName()).thenReturn(first);
        when(m.getLastName()).thenReturn(last);
        when(m.getAllInstruments()).thenReturn(List.of(instruments));
        return m;
    }

    private static InstrumentRoleMap mapRow(String sourceTag, String targetRolePattern) {
        InstrumentRoleMap row = mock(InstrumentRoleMap.class);
        when(row.getSourceTag()).thenReturn(sourceTag);
        when(row.getTargetRolePattern()).thenReturn(targetRolePattern);
        return row;
    }

    // --- normalize ----------------------------------------------------------

    @Nested
    class Normalize {
        @Test
        @DisplayName("stripuje diakrytyki i normalizuje case")
        void stripsDiacriticsAndCase() {
            assertThat(RoleInstrumentResolver.normalize("Trąbka  II")).isEqualTo("trabka ii");
            assertThat(RoleInstrumentResolver.normalize("Flet C")).isEqualTo("flet c");
        }

        @Test
        @DisplayName("pusto/null →empty string, nigdy NPE")
        void handlesNulls() {
            assertThat(RoleInstrumentResolver.normalize(null)).isEmpty();
            assertThat(RoleInstrumentResolver.normalize("   ")).isEmpty();
        }
    }

    // --- resolve ------------------------------------------------------------

    @Nested
    class ResolveInstrument {

        @Test
        @DisplayName("dopasowanie nazwy wygrywa z wierszem mapy")
        void exactNameMatchWins() {
            Instrument trabka = instrument(10, "Trąbka", null);
            Instrument kornet = instrument(11, "Kornet", trabka);
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(1L))
                    .thenReturn(List.of(trabka, kornet));
            InstrumentRoleMap row = mapRow("kornet", "trabka ii");
            when(roleMapRepository.findAllByBandId(1L)).thenReturn(List.of(row));

            assertThat(resolver.resolveInstrument(1L, "Trąbka")).contains(trabka);
        }

        @Test
        @DisplayName("wiersz instrument_role_map rozwiązuje rolę AI na instrument")
        void roleMapRowResolvesAiRole() {
            Instrument trabka = instrument(10, "Trąbka", null);
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(1L)).thenReturn(List.of(trabka));
            // alias tag „Kornet" mapuje się na rołę AI „Trąbka II"
            InstrumentRoleMap row = mapRow("Trąbka", "Trąbka II");
            when(roleMapRepository.findAllByBandId(1L)).thenReturn(List.of(row));

            RoleInstrumentResolver.Resolution r = resolver.resolve(1L, "Trąbka II");
            assertThat(r.isResolved()).isTrue();
            assertThat(r.instrumentOrNull()).isEqualTo(trabka);
        }

        @Test
        @DisplayName("root (nie-alias) wygrywa nad aliasem w tej samej roli")
        void rootBeatsAliasForTheSameRole() {
            Instrument trabka = instrument(10, "Trąbka", null);
            Instrument kornet = instrument(11, "Kornet", trabka);
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(1L))
                    .thenReturn(List.of(kornet, trabka)); // alias na pierwszym miejscu listy!
            InstrumentRoleMap rowKornet = mapRow("Kornet", "Trąbka II");
            InstrumentRoleMap rowTrabka = mapRow("Trąbka", "Trąbka II");
            when(roleMapRepository.findAllByBandId(1L)).thenReturn(List.of(rowKornet, rowTrabka));

            assertThat(resolver.resolveInstrument(1L, "Trąbka II")).contains(trabka);
        }

        @Test
        @DisplayName("rola bez dopasowania → unresolved (nul + pusta lista członków), bez wyjątku")
        void unresolvedRoleIsLegal() {
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(anyLong())).thenReturn(List.of());
            when(roleMapRepository.findAllByBandId(anyLong())).thenReturn(List.of());
            when(memberRepository.findAllActiveByBandId(anyLong())).thenReturn(List.of());

            RoleInstrumentResolver.Resolution r = resolver.resolve(1L, "Klarnet B");
            assertThat(r.isResolved()).isFalse();
            assertThat(r.instrumentOrNull()).isNull();
            assertThat(r.memberNames()).isEmpty();
        }
    }

    @Nested
    class Members {

        @Test
        @DisplayName("członek pokrywa rolę, gdy instrument z jego listy rozwiązuje tę rolę (mapa)")
        void memberCoversRoleViaMapRow() {
            Instrument kornet = instrument(11, "Kornet", null);
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(1L))
                    .thenReturn(List.of(kornet));
            InstrumentRoleMap row = mapRow("Kornet", "Trąbka II");
            when(roleMapRepository.findAllByBandId(1L)).thenReturn(List.of(row));
            Member m = member("Jan", "Kowalski", kornet);
            when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(m));

            RoleInstrumentResolver.Resolution r = resolver.resolve(1L, "Trąbka II");
            assertThat(r.memberNames()).containsExactly("Jan Kowalski");
        }

        @Test
        @DisplayName("członek bez pokrycia nie trafia na listę")
        void nonCoveringMemberExcluded() {
            Instrument flet = instrument(20, "Flet", null);
            when(instrumentRepository.findAllOrderBySortPriorityByBandId(1L)).thenReturn(List.of(flet));
            when(roleMapRepository.findAllByBandId(1L)).thenReturn(List.of());
            Member m = member("Anna", "Nowak", flet);
            when(memberRepository.findAllActiveByBandId(1L)).thenReturn(List.of(m));

            RoleInstrumentResolver.Resolution r = resolver.resolve(1L, "Trąbka II");
            assertThat(r.memberNames()).isEmpty();
        }
    }
}
