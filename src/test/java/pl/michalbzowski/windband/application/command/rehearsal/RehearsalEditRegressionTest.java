package pl.michalbzowski.windband.application.command.rehearsal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Regression test for GitHub Issue #5:
 * "Brak możliwości edycji Prób"
 *
 * Verifies that a scheduled rehearsal can be updated — date, times,
 * location, and notes can all be changed after initial creation.
 */
@Transactional
class RehearsalEditRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RehearsalCommandService commandService;

    @Test
    void shouldUpdateRehearsalDetails() {
        // Schedule a rehearsal first
        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(1));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setEndTime(LocalTime.of(20, 0));
        createCmd.setLocation("Sala 1");
        createCmd.setNotes("Próba ogólna");

        Rehearsal original = commandService.scheduleRehearsal(createCmd, 1L);
        Long id = original.getId();

        assertThat(id).isNotNull();
        assertThat(original.getLocation()).isEqualTo("Sala 1");

        // Now update the rehearsal
        ScheduleRehearsalCommand updateCmd = new ScheduleRehearsalCommand();
        updateCmd.setDate(LocalDate.now().plusDays(1));
        updateCmd.setStartTime(LocalTime.of(19, 0));
        updateCmd.setEndTime(LocalTime.of(21, 0));
        updateCmd.setLocation("Sala 2");
        updateCmd.setNotes("Próba generalna — zmieniona lokalizacja");

        Rehearsal updated = commandService.updateRehearsal(id, updateCmd);

        assertThat(updated).isNotNull();
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(19, 0));
        assertThat(updated.getEndTime()).isEqualTo(LocalTime.of(21, 0));
        assertThat(updated.getLocation()).isEqualTo("Sala 2");
        assertThat(updated.getNotes()).isEqualTo("Próba generalna — zmieniona lokalizacja");
    }

    @Test
    void shouldUpdateOnlyLocation() {
        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(3));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setLocation("Stare miejsce");

        Rehearsal original = commandService.scheduleRehearsal(createCmd, 1L);
        Long id = original.getId();

        ScheduleRehearsalCommand updateCmd = new ScheduleRehearsalCommand();
        updateCmd.setDate(LocalDate.now().plusDays(3));
        updateCmd.setStartTime(LocalTime.of(18, 0));
        updateCmd.setLocation("Nowe miejsce");

        Rehearsal updated = commandService.updateRehearsal(id, updateCmd);

        assertThat(updated.getLocation()).isEqualTo("Nowe miejsce");
        assertThat(updated.getStartTime()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void shouldThrowWhenUpdatingNonexistentRehearsal() {
        ScheduleRehearsalCommand updateCmd = new ScheduleRehearsalCommand();
        updateCmd.setDate(LocalDate.now());
        updateCmd.setStartTime(LocalTime.of(18, 0));

        assertThatExceptionOfType(RehearsalNotFoundException.class)
                .isThrownBy(() -> commandService.updateRehearsal(99999L, updateCmd));
    }

    @Test
    void shouldClearNotesOnUpdate() {
        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(5));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setLocation("Sala 1");
        createCmd.setNotes("Some notes");

        Rehearsal original = commandService.scheduleRehearsal(createCmd, 1L);
        assertThat(original.getNotes()).isEqualTo("Some notes");

        ScheduleRehearsalCommand updateCmd = new ScheduleRehearsalCommand();
        updateCmd.setDate(LocalDate.now().plusDays(5));
        updateCmd.setStartTime(LocalTime.of(18, 0));
        updateCmd.setLocation("Sala 1");
        updateCmd.setNotes(null);

        Rehearsal updated = commandService.updateRehearsal(original.getId(), updateCmd);

        assertThat(updated.getNotes()).isNull();
    }
}
