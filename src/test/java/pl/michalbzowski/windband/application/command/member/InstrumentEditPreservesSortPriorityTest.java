package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Regression test for issue #61:
 * "Edytowany Instrument trafia na koniec listy"
 * 
 * When editing an instrument without changing sortPriority,
 * the sortPriority should be preserved.
 */
@Transactional
class InstrumentEditPreservesSortPriorityTest extends BaseIntegrationTest {

    @Autowired
    private InstrumentCommandService commandService;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldPreserveSortPriorityWhenEditingWithoutChangingIt() {
        // Given: create instruments with different sort priorities
        // Note: shared Testcontainers may have leftover instruments from other test classes,
        // so we create unique names and find them after creation.
        long timestamp = System.currentTimeMillis();
        String name1 = "Trumpet-" + timestamp;
        String name2 = "Trombone-" + timestamp;
        String name3 = "Tuba-" + timestamp;

        commandService.createInstrument(name1, "Brass instrument", 1);
        commandService.createInstrument(name2, "Brass instrument", 2);
        commandService.createInstrument(name3, "Brass instrument", 3);

        // Find the first instrument's ID
        List<Instrument> allInstruments = instrumentRepository.findAllOrderBySortPriority();
        Instrument inst1 = allInstruments.stream()
                .filter(i -> name1.equals(i.getName())).findFirst().orElseThrow();

        // When: edit only the name of the first instrument (sortPriority should remain 1)
        commandService.updateInstrument(inst1.getId(), name1 + " Updated", "Brass instrument", 1);

        // Then: sort priority should be preserved — find our instruments in the full list
        List<Instrument> updatedInstruments = instrumentRepository.findAllOrderBySortPriority();
        Instrument updated1 = updatedInstruments.stream()
                .filter(i -> (name1 + " Updated").equals(i.getName())).findFirst().orElseThrow();
        Instrument inst2 = updatedInstruments.stream()
                .filter(i -> name2.equals(i.getName())).findFirst().orElseThrow();
        Instrument inst3 = updatedInstruments.stream()
                .filter(i -> name3.equals(i.getName())).findFirst().orElseThrow();

        assertThat(updated1.getSortPriority()).isEqualTo(1);

        // Verify ordering: our instrument with priority 1 should come before priority 2 and 3
        int idx1 = updatedInstruments.indexOf(updated1);
        int idx2 = updatedInstruments.indexOf(inst2);
        int idx3 = updatedInstruments.indexOf(inst3);
        assertThat(idx1).isLessThan(idx2);
        assertThat(idx2).isLessThan(idx3);
    }

    @Test
    void shouldPreserveSortPriorityWhenNullIsPassed() {
        // Given: create an instrument with sort priority
        Instrument inst = commandService.createInstrument("Flute", "Woodwind instrument", 5);

        // When: update with null sortPriority (simulating empty form field)
        commandService.updateInstrument(inst.getId(), "Flute Updated", "Woodwind instrument", null);

        // Then: sort priority should be preserved (not reset to 0)
        Instrument instrument = instrumentRepository.findById(inst.getId()).orElseThrow();
        assertThat(instrument.getSortPriority()).isEqualTo(5);
    }
}