package pl.michalbzowski.windband.application.query.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T1 — reguły spójności propozycji AI: nakładanie się zakresów, luki wewnętrzne,
 * przekroczenie liczby stron pliku i brak mapowania głosu. Czyste funkcje → test bez Spring-a.
 */
class ProposalConsistencyTest {

    private static ProposalConsistency.Row row(String role, int from, int to, boolean mapped) {
        return new ProposalConsistency.Row(role, from, to, mapped);
    }

    @Test
    void czystyZestawBezProblematWracapustyRaport() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto 1", 2, 8, true),
                row("Trąbka II", 9, 17, true),
                row("Tuba", 18, 24, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues()).allSatisfy(list -> assertThat(list).isEmpty());
        assertThat(report.errorRows()).isZero();
        assertThat(report.warningRows()).isZero();
    }

    @Test
    void nakladajaceSieZakresyOznaczaNaObuGlosachZWzajemnymPartnerem() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto 1", 1, 10, true),
                row("Puzon", 8, 15, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).extracting(ProposalConsistency.Issue::code)
                .containsOnly("OVERLAP");
        assertThat(report.issues().get(0).get(0).detail()).contains("Puzon").contains("8–15");
        assertThat(report.issues().get(1)).extracting(ProposalConsistency.Issue::code)
                .containsOnly("OVERLAP");
        assertThat(report.issues().get(1).get(0).detail()).contains("Alto 1").contains("1–10");
        assertThat(report.issues().get(0).get(0).severity()).isEqualTo("ERROR");
        assertThat(report.errorRows()).isEqualTo(2);
    }

    @Test
    void identyczneZakresyRoznychGlosowToTakzeNakladanie() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Trąbka I", 10, 17, true),
                row("Klarnet", 10, 17, false));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).extracting(ProposalConsistency.Issue::code).contains("OVERLAP");
        assertThat(report.issues().get(1)).extracting(ProposalConsistency.Issue::code).contains("OVERLAP");
    }

    @Test
    void stykajaceSieZakresyNieMajaNakladaniaANiluki() {
        // Koniec na 8, następny zaczynajacy od 9: pelne pokrycie bez przerw.
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto 1", 2, 8, true),
                row("Trąbka II", 9, 17, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues()).allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void lukaWewnetrznaOznaczanaNaGlosieZaczynajacymSiePoNiej() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto 1", 2, 8, true),
                row("Tuba", 12, 17, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).isEmpty();
        assertThat(report.issues().get(1)).extracting(ProposalConsistency.Issue::code).containsExactly("GAP");
        assertThat(report.issues().get(1).get(0).detail()).contains("9–11");
        assertThat(report.issues().get(1).get(0).severity()).isEqualTo("WARN");
        assertThat(report.warningRows()).isEqualTo(1);
    }

    @Test
    void lukaPrzedPierwszymGlosemJestLegalnaINieOznaczana() {
        // Strony tytulowe/dedykacje — pusty poczatek pliku to stan normalny.
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto 1", 5, 10, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues()).allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void zakresKonczacySiePoKoncuPlikuJestBledem() {
        List<ProposalConsistency.Row> rows = List.of(row("Tuba", 30, 40, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).extracting(ProposalConsistency.Issue::code).containsExactly("BEYOND_PAGES");
        assertThat(report.issues().get(0).get(0).detail()).contains("40").contains("36");
        assertThat(report.errorRows()).isEqualTo(1);
    }

    @Test
    void bezLiczbyStronPlikuCheckBeyondPagesNiedziala() {
        List<ProposalConsistency.Row> rows = List.of(row("Tuba", 30, 40, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, null);

        assertThat(report.issues()).allSatisfy(list -> assertThat(list).isEmpty());
    }

    @Test
    void glosBezMapowaniaNaZespolDostajeOstrzezenie() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Baryton", 18, 24, false),
                row("Tuba", 25, 30, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).extracting(ProposalConsistency.Issue::code).containsExactly("UNMAPPED");
        assertThat(report.issues().get(0).get(0).detail()).contains("instrument");
        assertThat(report.issues().get(1)).isEmpty();
        assertThat(report.warningRows()).isEqualTo(1);
    }

    @Test
    void wierszZBledemIOstrzezeniemLiczonyRazOWylicznieBledow() {
        List<ProposalConsistency.Row> rows = List.of(
                row("Tuba", 30, 40, false),
                row("Alto", 2, 29, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(0)).extracting(ProposalConsistency.Issue::code)
                .containsExactlyInAnyOrder("BEYOND_PAGES", "UNMAPPED");
        assertThat(report.errorRows()).isEqualTo(1);
        assertThat(report.warningRows()).isZero();
    }

    @Test
    void pustaILubNullListaWracajaPustyRaport() {
        ProposalConsistency.Report empty = ProposalConsistency.check(List.of(), 36);
        assertThat(empty.rowCount()).isZero();
        assertThat(empty.errorRows()).isZero();
        assertThat(empty.warningRows()).isZero();

        ProposalConsistency.Report nullList = ProposalConsistency.check(null, 36);
        assertThat(nullList.rowCount()).isZero();
        assertThat(nullList.errorRows()).isZero();
    }

    @Test
    void lukaPoCzesciowoPokrywajacychSielukachLiczySieGleboko() {
        // A 2–5 i B 4–9 pokrywaja sie czesciowo; C zaczyna na 12 → luka 10–11.
        List<ProposalConsistency.Row> rows = List.of(
                row("Alto", 2, 5, true),
                row("Baryton", 4, 9, true),
                row("Tuba", 12, 15, true));

        ProposalConsistency.Report report = ProposalConsistency.check(rows, 36);

        assertThat(report.issues().get(2)).extracting(ProposalConsistency.Issue::code).containsExactly("GAP");
        assertThat(report.issues().get(2).get(0).detail()).contains("10–11");
    }
}
