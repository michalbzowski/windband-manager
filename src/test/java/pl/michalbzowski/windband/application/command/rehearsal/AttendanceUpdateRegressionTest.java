package pl.michalbzowski.windband.application.command.rehearsal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.band.BandRepository;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRepository;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.rehearsal.RehearsalRepository;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.BaseIntegrationTest;

/**
 * Regression test for GitHub Issue #8:
 * "Zapisanie wprowadzonej już próby po zmianie obecności, nie zmienia tej obecności"
 *
 * Verifies that after recording attendance for a member in a rehearsal,
 * updating that attendance (via the same endpoint /api/rehearsals/{id}/attendance)
 * correctly changes the attendance status.
 *
 * Root cause: the inline dropdown change handler in detail.html used HTTP PATCH,
 * but RehearsalController only accepts POST on the /attendance endpoint.
 * The fix changes the JS fetch to use POST, matching the controller mapping.
 *
 * This test verifies the domain-layer updateAttendance logic works correctly
 * and the commandService.recordAttendance properly updates existing records.
 */
@Transactional
class AttendanceUpdateRegressionTest extends BaseIntegrationTest {

    @Autowired
    private RehearsalCommandService commandService;

    @Autowired
    private RehearsalRepository rehearsalRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BandRepository bandRepository;

    @Test
    void shouldUpdateAttendanceAfterInitialRecord() {
        // Setup: create band, member, and rehearsal
        Band band = bandRepository.save(Band.create("Test Band", "test-band-8"));
        Member member = memberRepository.save(Member.create(
                "Jan", "Kowalski", LocalDate.of(1990, 1, 1), band));

        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(1));
        createCmd.setStartTime(LocalTime.of(18, 0));
        createCmd.setLocation("Sala 1");
        Rehearsal rehearsal = commandService.scheduleRehearsal(createCmd, 1L);

        // Record initial attendance as PRESENT
        RecordAttendanceCommand recordCmd = new RecordAttendanceCommand();
        recordCmd.setRehearsalId(rehearsal.getId());
        recordCmd.setMemberId(member.getId());
        recordCmd.setStatus("PRESENT");
        commandService.recordAttendance(recordCmd);

        // Verify initial status
        Rehearsal afterRecord = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        assertThat(afterRecord.getAttendances()).hasSize(1);
        assertThat(afterRecord.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);

        // Now update attendance to EXCUSED (this is what the dropdown change does)
        RecordAttendanceCommand updateCmd = new RecordAttendanceCommand();
        updateCmd.setRehearsalId(rehearsal.getId());
        updateCmd.setMemberId(member.getId());
        updateCmd.setStatus("EXCUSED");
        commandService.recordAttendance(updateCmd);

        // Verify attendance was updated — THIS is the core assertion for Issue #8
        Rehearsal afterUpdate = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        assertThat(afterUpdate.getAttendances()).hasSize(1);
        assertThat(afterUpdate.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
    }

    @Test
    void shouldUpdateAttendanceMultipleTimes() {
        Band band = bandRepository.save(Band.create("Test Band 2", "test-band-8b"));
        Member member = memberRepository.save(Member.create(
                "Anna", "Nowak", LocalDate.of(1995, 5, 15), band));

        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(2));
        createCmd.setStartTime(LocalTime.of(19, 0));
        createCmd.setLocation("Sala 2");
        Rehearsal rehearsal = commandService.scheduleRehearsal(createCmd, 1L);

        // Record as PRESENT
        RecordAttendanceCommand cmd1 = new RecordAttendanceCommand();
        cmd1.setRehearsalId(rehearsal.getId());
        cmd1.setMemberId(member.getId());
        cmd1.setStatus("PRESENT");
        commandService.recordAttendance(cmd1);

        // Update to UNEXCUSED
        RecordAttendanceCommand cmd2 = new RecordAttendanceCommand();
        cmd2.setRehearsalId(rehearsal.getId());
        cmd2.setMemberId(member.getId());
        cmd2.setStatus("UNEXCUSED");
        commandService.recordAttendance(cmd2);

        Rehearsal afterFirstUpdate = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        assertThat(afterFirstUpdate.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.UNEXCUSED);

        // Update again to EXCUSED
        RecordAttendanceCommand cmd3 = new RecordAttendanceCommand();
        cmd3.setRehearsalId(rehearsal.getId());
        cmd3.setMemberId(member.getId());
        cmd3.setStatus("EXCUSED");
        commandService.recordAttendance(cmd3);

        Rehearsal afterSecondUpdate = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        assertThat(afterSecondUpdate.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
        // Should still be exactly one attendance record — no duplicates
        assertThat(afterSecondUpdate.getAttendances()).hasSize(1);
    }

    @Test
    void shouldRecordNewAttendanceForMemberNotYetRecorded() {
        // When a member has no attendance yet, the updateAttendance path should create one
        Band band = bandRepository.save(Band.create("Test Band 3", "test-band-8c"));
        Member member = memberRepository.save(Member.create(
                "Piotr", "Wiśniewski", LocalDate.of(1985, 3, 20), band));

        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(3));
        createCmd.setStartTime(LocalTime.of(17, 0));
        createCmd.setLocation("Sala 3");
        Rehearsal rehearsal = commandService.scheduleRehearsal(createCmd, 1L);

        // Record attendance for first time via the "update" path (no prior attendance)
        RecordAttendanceCommand cmd = new RecordAttendanceCommand();
        cmd.setRehearsalId(rehearsal.getId());
        cmd.setMemberId(member.getId());
        cmd.setStatus("PRESENT");
        commandService.recordAttendance(cmd);

        Rehearsal result = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        assertThat(result.getAttendances()).hasSize(1);
        assertThat(result.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void shouldUpdateAttendanceWithoutCreatingDuplicates() {
        // Regression: updating attendance must NOT create a second record
        Band band = bandRepository.save(Band.create("Test Band 4", "test-band-8d"));
        Member member = memberRepository.save(Member.create(
                "Maria", "Zielińska", LocalDate.of(1992, 8, 10), band));

        ScheduleRehearsalCommand createCmd = new ScheduleRehearsalCommand();
        createCmd.setDate(LocalDate.now().plusDays(4));
        createCmd.setStartTime(LocalTime.of(18, 30));
        createCmd.setLocation("Sala 4");
        Rehearsal rehearsal = commandService.scheduleRehearsal(createCmd, 1L);

        // Record initial
        RecordAttendanceCommand cmd1 = new RecordAttendanceCommand();
        cmd1.setRehearsalId(rehearsal.getId());
        cmd1.setMemberId(member.getId());
        cmd1.setStatus("NO_RESPONSE");
        commandService.recordAttendance(cmd1);

        // Update
        RecordAttendanceCommand cmd2 = new RecordAttendanceCommand();
        cmd2.setRehearsalId(rehearsal.getId());
        cmd2.setMemberId(member.getId());
        cmd2.setStatus("PRESENT");
        commandService.recordAttendance(cmd2);

        Rehearsal result = rehearsalRepository.findById(rehearsal.getId()).orElseThrow();
        // Must be exactly 1 attendance record — not 2
        assertThat(result.getAttendances()).hasSize(1);
        assertThat(result.getAttendances().get(0).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.getPresentCount()).isEqualTo(1);
    }
}
