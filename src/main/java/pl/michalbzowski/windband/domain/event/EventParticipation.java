package pl.michalbzowski.windband.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;

@Entity
@Table(name = "event_participations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"event_id", "member_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private BandEvent bandEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipationResponse response;

    private BigDecimal paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    EventParticipation(BandEvent bandEvent, Member member) {
        this.bandEvent = bandEvent;
        this.member = member;
        this.response = ParticipationResponse.NO_RESPONSE;
        this.paymentStatus = PaymentStatus.NOT_APPLICABLE;
    }

    void setResponse(ParticipationResponse response) {
        this.response = response;
    }

    public void recordPayment(BigDecimal amount) {
        this.paymentAmount = amount;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    void markPaymentPaid() {
        if (paymentStatus != PaymentStatus.PENDING) {
            throw new IllegalStateException("No pending payment to mark as paid");
        }
        this.paymentStatus = PaymentStatus.PAID;
    }
}
