package pl.michalbzowski.windband.domain.rehearsal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Entity
@Table(name = "rehearsals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rehearsal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    private LocalTime endTime;

    private String location;

    private String notes;

    @OneToMany(mappedBy = "rehearsal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Attendance> attendances = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    private Rehearsal(LocalDate date, LocalTime startTime, String location, Band band) {
        this.date = Objects.requireNonNull(date, "date required");
        this.startTime = Objects.requireNonNull(startTime, "startTime required");
        this.location = location;
        this.band = Objects.requireNonNull(band, "band required");
    }

    public static Rehearsal schedule(LocalDate date, LocalTime startTime, String location, Band band) {
        return new Rehearsal(date, startTime, location, band);
    }

    public void updateTime(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void updateLocation(String location) {
        this.location = location;
    }

    public void updateNotes(String notes) {
        this.notes = notes;
    }

    public void recordAttendance(Member member, AttendanceStatus status) {
        boolean alreadyRecorded = attendances.stream()
                .anyMatch(a -> a.getMember().equals(member));
        if (alreadyRecorded) {
            throw new IllegalStateException("Attendance already recorded for member: " + member.getId());
        }
        attendances.add(new Attendance(this, member, status));
    }

    /**
     * Invite a member to this rehearsal. Mirrors the {@code BandEvent.inviteMember}
     * pattern: idempotent for repeat calls (silently skips members who already
     * have an attendance row), throws if the same member is invited again
     * through a different path. The new attendance row defaults to
     * {@code NO_RESPONSE} so the member can later be marked PRESENT/EXCUSED/etc.
     */
    public void inviteMember(Member member) {
        boolean alreadyInvited = attendances.stream()
                .anyMatch(a -> a.getMember().equals(member));
        if (alreadyInvited) {
            throw new IllegalStateException("Member already invited: " + member.getId());
        }
        attendances.add(new Attendance(this, member, AttendanceStatus.NO_RESPONSE));
    }

    public void updateAttendance(Member member, AttendanceStatus status) {
        attendances.stream()
                .filter(a -> a.getMember().equals(member))
                .findFirst()
                .ifPresentOrElse(
                        a -> a.updateStatus(status),
                        () -> attendances.add(new Attendance(this, member, status))
                );
    }

    public long getPresentCount() {
        return attendances.stream()
                .filter(a -> a.getStatus() == AttendanceStatus.PRESENT)
                .count();
    }
}
