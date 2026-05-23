package pl.michalbzowski.windband.domain.rehearsal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;

@Entity
@Table(name = "attendances", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"rehearsal_id", "member_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rehearsal_id", nullable = false)
    private Rehearsal rehearsal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceStatus status;

    Attendance(Rehearsal rehearsal, Member member, AttendanceStatus status) {
        this.rehearsal = rehearsal;
        this.member = member;
        this.status = status;
    }

    void updateStatus(AttendanceStatus status) {
        this.status = status;
    }
}
