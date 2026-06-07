package pl.michalbzowski.windband.application.command.member;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.michalbzowski.windband.domain.member.Instrument;
import pl.michalbzowski.windband.domain.member.InstrumentRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regression test for issue #61:
 * "Edytowany Instrument trafia na koniec listy"
 * 
 * When editing an instrument without changing sortPriority,
 * the sortPriority should be preserved.
 */
@SpringBootTest
@Testcontainers
@Transactional
class InstrumentEditPreservesSortPriorityTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("windband_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private InstrumentCommandService commandService;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Test
    void shouldPreserveSortPriorityWhenEditingWithoutChangingIt() {
        // Given: create instruments with different sort priorities
        Instrument inst1 = commandService.createInstrument("Trumpet", "Brass instrument", 1);
        Instrument inst2 = commandService.createInstrument("Trombone", "Brass instrument", 2);
        Instrument inst3 = commandService.createInstrument("Tuba", "Brass instrument", 3);

        // When: edit only the name of the first instrument (sortPriority should remain 1)
        commandService.updateInstrument(inst1.getId(), "Trumpet Updated", "Brass instrument", 1);

        // Then: sort priority should be preserved
        List<Instrument> instruments = instrumentRepository.findAllOrderBySortPriority();
        assertThat(instruments).hasSize(3);
        assertThat(instruments.get(0).getName()).isEqualTo("Trumpet Updated");
        assertThat(instruments.get(0).getSortPriority()).isEqualTo(1);
        assertThat(instruments.get(1).getName()).isEqualTo("Trombone");
        assertThat(instruments.get(2).getName()).isEqualTo("Tuba");
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