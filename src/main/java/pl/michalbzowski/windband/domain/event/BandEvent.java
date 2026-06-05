package pl.michalbzowski.windband.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Entity
@Table(name = "band_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BandEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime startTime;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType paymentType = PaymentType.FREE;

    private BigDecimal paymentAmount;

    private String notes;

    @OneToMany(mappedBy = "bandEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventParticipation> participations = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    private BandEvent(String name, LocalDate date, LocalTime startTime,
                      String location, EventType eventType, Band band,
                      PaymentType paymentType, BigDecimal paymentAmount) {
        this.name = Objects.requireNonNull(name, "name required");
        this.date = Objects.requireNonNull(date, "date required");
        this.startTime = startTime;
        this.location = location;
        this.eventType = Objects.requireNonNull(eventType, "eventType required");
        this.paymentType = paymentType != null ? paymentType : PaymentType.FREE;
        this.paymentAmount = paymentAmount;
        this.band = Objects.requireNonNull(band, "band required");
        this.paymentType = paymentType != null ? paymentType : PaymentType.FREE;
        this.paymentAmount = paymentAmount;
    }

    public static BandEvent create(String name, LocalDate date, LocalTime startTime,
                                   String location, EventType eventType, Band band,
                                   PaymentType paymentType, BigDecimal paymentAmount) {
        return new BandEvent(name, date, startTime, location, eventType, band, paymentType, paymentAmount);
    }

    public void updateDetails(String name, LocalDate date, LocalTime startTime, String location) {
        this.name = Objects.requireNonNull(name);
        this.date = Objects.requireNonNull(date);
        this.startTime = startTime;
        this.location = location;
    }

    public void updatePaymentDetails(PaymentType paymentType, BigDecimal paymentAmount) {
        this.paymentType = paymentType != null ? paymentType : PaymentType.FREE;
        this.paymentAmount = paymentAmount;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public BigDecimal getPayoutPerMember() {
        if (paymentType != PaymentType.PAID_SPLIT || paymentAmount == null) {
            return null;
        }
        long confirmed = getConfirmedCount();
        if (confirmed == 0) {
            return null;
        }
        return paymentAmount.divide(BigDecimal.valueOf(confirmed), 2, java.math.RoundingMode.HALF_UP);
    }

    public void inviteMember(Member member) {
        boolean alreadyInvited = participations.stream()
                .anyMatch(p -> p.getMember().equals(member));
        if (alreadyInvited) {
            throw new IllegalStateException("Member already invited: " + member.getId());
        }
        participations.add(new EventParticipation(this, member));
    }

    public void recordResponse(Member member, ParticipationResponse response) {
        findParticipation(member).setResponse(response);
    }

    public void recordPayment(Member member, BigDecimal amount) {
        EventParticipation participation = findParticipation(member);
        participation.recordPayment(amount);
    }

    public void markPaymentPaid(Member member) {
        findParticipation(member).markPaymentPaid();
    }

    public long getConfirmedCount() {
        return participations.stream()
                .filter(p -> p.getResponse() == ParticipationResponse.CONFIRMED)
                .count();
    }

    public long getDeclinedCount() {
        return participations.stream()
                .filter(p -> p.getResponse() == ParticipationResponse.DECLINED)
                .count();
    }

    public long getNoResponseCount() {
        return participations.stream()
                .filter(p -> p.getResponse() == ParticipationResponse.NO_RESPONSE)
                .count();
    }

    private EventParticipation findParticipation(Member member) {
        return participations.stream()
                .filter(p -> p.getMember().equals(member))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Member not invited to event: " + member.getId()));
    }
}
