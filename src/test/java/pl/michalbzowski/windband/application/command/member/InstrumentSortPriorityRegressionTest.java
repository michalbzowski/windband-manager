package pl.michalbzowski.windband.domain.member;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for instrument sort priority feature.
 * Tests that:
 * 1. Instruments can be created with sort priority
 * 2. Instruments can be updated with sort priority
 * 3. Sort priority is persisted correctly
 */
public class InstrumentSortPriorityRegressionTest {

    @Test
    void shouldCreateInstrumentWithSortPriority() {
        // Given: creating an instrument
        Instrument instrument = Instrument.create("Trąbka");

        // When: updating sort priority
        instrument.updateSortPriority(5);

        // Then: the sort priority should be set correctly
        assertThat(instrument.getSortPriority()).isEqualTo(5);
    }

    @Test
    void shouldUpdateInstrumentSortPriority() {
        // Given: an instrument exists
        Instrument instrument = Instrument.create("Trąbka");

        // When: updating sort priority multiple times
        instrument.updateSortPriority(5);
        instrument.updateSortPriority(10);

        // Then: the sort priority should be updated to latest value
        assertThat(instrument.getSortPriority()).isEqualTo(10);
    }

    @Test
    void shouldDefaultSortPriorityToZero() {
        // Given: creating an instrument
        Instrument instrument = Instrument.create("Trąbka");

        // Then: the default sort priority should be 0
        assertThat(instrument.getSortPriority()).isEqualTo(0);
    }

    @Test
    void shouldHandleNullSortPriority() {
        // Given: an instrument exists
        Instrument instrument = Instrument.create("Trąbka");

        // When: updating with null sort priority
        instrument.updateSortPriority(null);

        // Then: should default to 0
        assertThat(instrument.getSortPriority()).isEqualTo(0);
    }
}