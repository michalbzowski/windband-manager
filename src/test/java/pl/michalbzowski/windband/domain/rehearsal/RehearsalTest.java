package pl.michalbzowski.windband.domain.rehearsal;

import org.junit.jupiter.api.Test;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.member.MemberRole;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

class RehearsalTest {

    @Test
    void shouldScheduleRehearsal() {
        LocalDate date = LocalDate.now().plusDays(7);
        Rehearsal rehearsal = Rehearsal.schedule(date, LocalTime.of(18, 0), "Sala prób");

        assertThat(rehearsal.getDate()).isEqualTo(date);
        assertThat(rehearsal.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(rehearsal.getLocation()).isEqualTo("Sala prób");
    }

    @Test
    void shouldThrowWhenSchedulingInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> Rehearsal.schedule(pastDate, LocalTime.of(18, 0), "Sala prób"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("past");
    }

    @Test
    void shouldRecordAttendance() {
        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.now().plusDays(7), LocalTime.of(18, 0), "Sala prób");
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        rehearsal.recordAttendance(member, AttendanceStatus.PRESENT);

        assertThat(rehearsal.getPresentCount()).isEqualTo(1);
    }

    @Test
    void shouldUpdateAttendance() {
        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.now().plusDays(7), LocalTime.of(18, 0), "Sala prób");
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        rehearsal.recordAttendance(member, AttendanceStatus.NO_RESPONSE);
        rehearsal.updateAttendance(member, AttendanceStatus.PRESENT);

        assertThat(rehearsal.getPresentCount()).isEqualTo(1);
    }

    @Test
    void shouldThrowWhenRecordingDuplicateAttendance() {
        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.now().plusDays(7), LocalTime.of(18, 0), "Sala prób");
        Member member = Member.create("Jan", "Kowalski",
                LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);

        rehearsal.recordAttendance(member, AttendanceStatus.PRESENT);

        assertThatThrownBy(() -> rehearsal.recordAttendance(member, AttendanceStatus.EXCUSED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already recorded");
    }

    @Test
    void shouldCountPresentCorrectly() {
        Rehearsal rehearsal = Rehearsal.schedule(
                LocalDate.now().plusDays(7), LocalTime.of(18, 0), "Sala prób");
        Member m1 = Member.create("Jan", "Kowalski", LocalDate.of(1990, 1, 15), MemberRole.MEMBER, true);
        Member m2 = Member.create("Piotr", "Nowak", LocalDate.of(1985, 6, 20), MemberRole.MEMBER, false);
        Member m3 = Member.create("Anna", "Wiśniewska", LocalDate.of(1995, 3, 10), MemberRole.MEMBER, false);

        rehearsal.recordAttendance(m1, AttendanceStatus.PRESENT);
        rehearsal.recordAttendance(m2, AttendanceStatus.PRESENT);
        rehearsal.recordAttendance(m3, AttendanceStatus.EXCUSED);

        assertThat(rehearsal.getPresentCount()).isEqualTo(2);
    }
}
