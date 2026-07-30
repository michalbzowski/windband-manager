package pl.michalbzowski.windband.application.command.rehearsal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Regression test for GitHub Issue #4:
 * "Dodanie kolejnej próby zapisuje jej wielokrotność"
 *
 * Verifies that each call to scheduleRehearsal creates exactly one rehearsal,
 * and that saving multiple rehearsals results in the correct count.
 * The frontend bug caused duplicate event listeners which triggered
 * multiple POST requests for a single user action. Each API call should
 * create exactly one rehearsal — no more, no less.
 */
@Transactional
class RehearsalCreationRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RehearsalCommandService commandService;

    @Autowired
    private RehearsalRepository rehearsalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Clean rehearsals table before each test to avoid data pollution
        // from data.sql and other tests in shared Testcontainers instance
        // Must delete attendances first due to FK constraint
        jdbcTemplate.execute("DELETE FROM attendances");
        jdbcTemplate.execute("DELETE FROM rehearsals");
    }

    @Test
    void shouldCreateExactlyOneRehearsalPerCall() {
        // Create first rehearsal — should work fine
        ScheduleRehearsalCommand cmd1 = new ScheduleRehearsalCommand();
        cmd1.setDate(LocalDate.now().plusDays(1));
        cmd1.setStartTime(LocalTime.of(18, 0));
        cmd1.setEndTime(LocalTime.of(20, 0));
        cmd1.setLocation("Sala 1");

        Rehearsal r1 = commandService.scheduleRehearsal(cmd1, 1L);

        assertThat(r1).isNotNull();
        assertThat(r1.getId()).isNotNull();
        assertThat(r1.getDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(r1.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(r1.getLocation()).isEqualTo("Sala 1");

        // Create second rehearsal — should create exactly one, not duplicates
        ScheduleRehearsalCommand cmd2 = new ScheduleRehearsalCommand();
        cmd2.setDate(LocalDate.now().plusDays(2));
        cmd2.setStartTime(LocalTime.of(19, 0));
        cmd2.setLocation("Sala 2");

        Rehearsal r2 = commandService.scheduleRehearsal(cmd2, 1L);

        assertThat(r2).isNotNull();
        assertThat(r2.getId()).isNotNull();
        assertThat(r2.getId()).isNotEqualTo(r1.getId());
        assertThat(r2.getDate()).isEqualTo(LocalDate.now().plusDays(2));
        assertThat(r2.getLocation()).isEqualTo("Sala 2");

        // Verify we have exactly 2 rehearsals in the repository
        List<Rehearsal> all = rehearsalRepository.findAllOrderByDateDesc();
        assertThat(all).hasSize(2);
    }

    @Test
    void shouldCreateMultipleRehearsalsWithoutDuplicates() {
        // Simulate the bug scenario: multiple rapid calls
        // (as would happen with duplicate event listeners)
        for (int i = 1; i <= 3; i++) {
            ScheduleRehearsalCommand cmd = new ScheduleRehearsalCommand();
            cmd.setDate(LocalDate.now().plusDays(i));
            cmd.setStartTime(LocalTime.of(18, 0));
            cmd.setLocation("Sala " + i);

            Rehearsal r = commandService.scheduleRehearsal(cmd, 1L);
            assertThat(r).isNotNull();
            assertThat(r.getId()).isNotNull();
        }

        // Should have exactly 3 rehearsals — no duplicates
        List<Rehearsal> all = rehearsalRepository.findAllOrderByDateDesc();
        assertThat(all).hasSize(3);

        // Verify all have distinct IDs
        long distinctIds = all.stream().map(Rehearsal::getId).distinct().count();
        assertThat(distinctIds).isEqualTo(3);
    }

    @Test
    void shouldPreserveRehearsalDataCorrectly() {
        ScheduleRehearsalCommand cmd = new ScheduleRehearsalCommand();
        cmd.setDate(LocalDate.of(2026, 6, 15));
        cmd.setStartTime(LocalTime.of(17, 30));
        cmd.setEndTime(LocalTime.of(19, 30));
        cmd.setLocation("DK Psary");
        cmd.setNotes("Próba generalna");

        Rehearsal r = commandService.scheduleRehearsal(cmd, 1L);

        assertThat(r.getDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(r.getStartTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(r.getEndTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(r.getLocation()).isEqualTo("DK Psary");
        assertThat(r.getNotes()).isEqualTo("Próba generalna");
        assertThat(r.getAttendances()).isEmpty();
    }
}
