package pl.michalbzowski.windband.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unitowe testy {@link ByteSizeFormatter} — cecha: bramka PIT na pakiecie util
 * (PIT 18% w baseline, Radar Vol.35). Asserty pokrywają każdy warunek i gałąź
 * formatowania: progi (0, 1024), dobór jednostki, zaokrąglenie 1 miejsca,
 * przycinanie ".0" i niezależność od locale (Locale.ROOT vs przecinek PL).
 */
class ByteSizeFormatterTest {

    @ParameterizedTest
    @CsvSource({
            "0,      '0 B'",        // zero i poniżej — fallback bez rzutu jednostki
            "-1,     '0 B'",
            "-4096,  '0 B'",
            "1,      '1 B'",        // dolna granica przedziału bajtów
            "511,    '511 B'",
            "512,    '512 B'",
            "1023,   '1023 B'",     // górna granica BEFORE próg KB
            "1024,   '1 KB'",       // próg KB: 1.0 -> ".0" przycięty
            "1536,   '1.5 KB'",     // zaokrąglenie 1 miejsce, bez przycięcia
            "10752,  '10.5 KB'",
            "1048576,    '1 MB'",   // dokładnie 1 MB (przelot przez unit++)
            "14680064,   '14 MB'",  // 14.0 -> przycięte
            "1610612736, '1.5 GB'",
            "1099511627776, '1 TB'",
            "2199023255552, '2 TB'", // powyżej TB jednostka NIE rośnie dalej
            "9007199254740992, '8192 TB'" // sufit: największa dostępna jednostka
    })
    void humanRenderujeDeterministycznie(long bytes, String expected) {
        assertThat(ByteSizeFormatter.human(bytes)).isEqualTo(expected);
    }

    @Test
    void roundingJednegoMiejscaNieWynosiProgu() {
        // 1023.6 KB: "%.1f" zaokrągla do "1023.6" — jednostka zostaje KB (nie eskaluje do MB)
        assertThat(ByteSizeFormatter.human(1024L * 1024 - 400)).isEqualTo("1023.6 KB");
    }

    @Test
    void separatorDziesietnyJestZawszeKropka() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("pl-PL")); // format w PL dałby "1,5 KB"
            assertThat(ByteSizeFormatter.human(1536)).isEqualTo("1.5 KB");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void klasaJestUtilityBezPublicznegoKonstruktora() {
        assertThat(ByteSizeFormatter.class.getDeclaredConstructors()).hasSize(1);
        assertThat(java.lang.reflect.Modifier.isPrivate(
                ByteSizeFormatter.class.getDeclaredConstructors()[0].getModifiers())).isTrue();
    }
}
