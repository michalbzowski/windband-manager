package pl.michalbzowski.windband.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "event_invitations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "member_id"}),
        @UniqueConstraint(columnNames = {"token"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private BandEvent bandEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Member member;

    @Column(nullable = false, length = 36)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    private LocalDateTime sentAt;

    private LocalDateTime respondedAt;

    @Column(nullable = false, length = 20)
    private String preferredChannel;

    EventInvitation(BandEvent bandEvent, Member member, String preferredChannel) {
        this.bandEvent = Objects.requireNonNull(bandEvent, "bandEvent required");
        this.member = Objects.requireNonNull(member, "member required");
        this.token = UUID.randomUUID().toString();
        this.status = NotificationStatus.NOT_SENT;
        this.preferredChannel = Objects.requireNonNull(preferredChannel, "preferredChannel required");
    }

    public static EventInvitation create(BandEvent bandEvent, Member member) {
        return new EventInvitation(bandEvent, member, "EMAIL");
    }

    public static EventInvitation create(BandEvent bandEvent, Member member, String preferredChannel) {
        return new EventInvitation(bandEvent, member, preferredChannel);
    }

    public void markQueued() {
        this.status = NotificationStatus.QUEUED;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Resets a SENT invitation back to NOT_SENT, allowing it to be re-sent.
     * Only works if the invitation was previously sent — no-op otherwise.
     * Used when an event is updated and previously notified members
     * should receive an update notification.
     */
    public void markForResend() {
        if (status == NotificationStatus.SENT) {
            this.status = NotificationStatus.NOT_SENT;
            this.sentAt = null;
        }
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    public void markResponded() {
        this.respondedAt = LocalDateTime.now();
    }

    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }

    public boolean isNotSent() {
        return status == NotificationStatus.NOT_SENT;
    }

    public boolean isFailed() {
        return status == NotificationStatus.FAILED;
    }
}
