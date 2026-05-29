package pl.michalbzowski.windband.domain.rehearsal;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class RehearsalTest {

    private static final Band DEFAULT_BAND = Band.create("Test Band");

    @Test
    void shouldScheduleRehearsalForToday() {
        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.now(),
                LocalTime.of(18, 0),
                "Sala 1",
                DEFAULT_BAND
        );

        assertThat(rehearsal.getDate()).isEqualTo(LocalDate.now());
        assertThat(rehearsal.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(rehearsal.getLocation()).isEqualTo("Sala 1");
    }

    @Test
    void shouldScheduleRehearsalForYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        Rehearsal rehearsal = Rehearsal.schedule(
                yesterday,
                LocalTime.of(18, 0),
                "Sala 1",
                DEFAULT_BAND
        );

        assertThat(rehearsal.getDate()).isEqualTo(yesterday);
        assertThat(rehearsal.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(rehearsal.getLocation()).isEqualTo("Sala 1");
    }

    @Test
    void shouldScheduleRehearsalForPastDate() {
        LocalDate lastWeek = LocalDate.now().minusDays(7);

        Rehearsal rehearsal = Rehearsal.schedule(
                lastWeek,
                LocalTime.of(19, 30),
                "Sala prób",
                DEFAULT_BAND
        );

        assertThat(rehearsal.getDate()).isEqualTo(lastWeek);
        assertThat(rehearsal.getStartTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(rehearsal.getLocation()).isEqualTo("Sala prób");
    }

    @Test
    void shouldScheduleRehearsalForFutureDate() {
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        Rehearsal rehearsal = Rehearsal.schedule(
                nextWeek,
                LocalTime.of(18, 0),
                "Sala 2",
                DEFAULT_BAND
        );

        assertThat(rehearsal.getDate()).isEqualTo(nextWeek);
    }

    @Test
    void shouldFailWhenDateIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> Rehearsal.schedule(null, LocalTime.of(18, 0), "Sala", DEFAULT_BAND))
                .withMessageContaining("date required");
    }

    @Test
    void shouldFailWhenStartTimeIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> Rehearsal.schedule(LocalDate.now(), null, "Sala", DEFAULT_BAND))
                .withMessageContaining("startTime required");
    }

    @Test
    void shouldFailWhenBandIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> Rehearsal.schedule(LocalDate.now(), LocalTime.of(18, 0), "Sala", null))
                .withMessageContaining("band required");
    }
}
