package pl.michalbzowski.windband.application.command.scoreanalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.application.dto.scoreanalysis.AIProposals;
import pl.michalbzowski.windband.domain.composition.PartSource;

/**
 * Phase B — pins the {@code arrangement.json} contract after the parser rewrite
 * (the hand-rolled tokenizer it replaces could not parse nested objects at all).
 */
class JsonAiProposalsTest {

    @Test
    @DisplayName("Kanoniczny artefakt HttpScoreAnalyzerRunnera parsuje się wiersz po wierszu")
    void parsesCanonicalArray() {
        String json = """
                [
                  {"role": "Partytura", "pageFrom": 1, "pageTo": 15, "confidence": 0.95},
                  {"role": "Flet C", "pageFrom": 16, "pageTo": 17, "confidence": 0.95}
                ]""";
        List<AIProposals> rows = JsonAiProposals.parse(json);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).role()).isEqualTo("Partytura");
        assertThat(rows.get(0).pageFrom()).isEqualTo(1);
        assertThat(rows.get(0).pageTo()).isEqualTo(15);
        assertThat(rows.get(0).confidence()).isEqualTo(0.95d);
        assertThat(rows.get(0).source()).isEqualTo(PartSource.AI);
        assertThat(rows.get(1).role()).isEqualTo("Flet C");
    }

    @Test
    @DisplayName("Luźne nazwy kluczy (page_from/to/instrument) i wielkość liter są tolerowane")
    void tolerantKeyNames() {
        List<AIProposals> rows = JsonAiProposals.parse(
                "[{\"INSTRUMENT\":\"Klarnet B\",\"PAGE_FROM\":3,\"TO\":5,\"Confidence\":0.7}]");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).role()).isEqualTo("Klarnet B");
        assertThat(rows.get(0).pageFrom()).isEqualTo(3);
        assertThat(rows.get(0).pageTo()).isEqualTo(5);
        assertThat(rows.get(0).confidence()).isEqualTo(0.7d);
    }

    @Test
    @DisplayName("Braki i niedorzeczności AI są clampowane: strony ≥1, from≤to, confidence∈[0,1]")
    void clampsSloppyValues() {
        List<AIProposals> rows = JsonAiProposals.parse(
                "[{\"role\":\"Tuba\",\"pageFrom\":9,\"pageTo\":2,\"confidence\":7.0}]");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).pageFrom()).isEqualTo(9);
        assertThat(rows.get(0).pageTo()).isEqualTo(9);      // to ≥ from
        assertThat(rows.get(0).confidence()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("Wartości domyślne: brak pól → strona 1, confidence 1.0")
    void defaultsForMissingFields() {
        List<AIProposals> rows = JsonAiProposals.parse("[{\"role\":\"Sax\"}]");
        assertThat(rows.get(0).pageFrom()).isEqualTo(1);
        assertThat(rows.get(0).pageTo()).isEqualTo(1);
        assertThat(rows.get(0).confidence()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("Wiersz bez roli oraz śmieciowy/pusty JSON → IllegalArgumentException")
    void rejectsUnusableInput() {
        assertThatThrownBy(() -> JsonAiProposals.parse("[{\"pageFrom\":1}]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("role");
        assertThatThrownBy(() -> JsonAiProposals.parse("to nie jest json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonAiProposals.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonAiProposals.parse("42"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
