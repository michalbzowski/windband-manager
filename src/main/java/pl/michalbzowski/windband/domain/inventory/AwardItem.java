package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "award_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AwardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member assignedMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(name = "date_awarded")
    private LocalDate dateAwarded;

    @Column(name = "order_number")
    private String orderNumber;

    protected AwardItem(Band band) {
        this.band = Objects.requireNonNull(band, "band required");
    }

    public static AwardItem create(Band band) {
        return new AwardItem(band);
    }

    public void assignTo(Member member) {
        this.assignedMember = member;
    }

    public void unassign() {
        this.assignedMember = null;
    }

    public void updateDetails(String name, String description) {
        // name parameter kept for backward compatibility but ignored
        this.description = description;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public void setDateAwarded(LocalDate dateAwarded) {
        this.dateAwarded = dateAwarded;
    }

    public boolean isAssigned() {
        return assignedMember != null;
    }

    @Transient
    public String getAssignedMemberName() {
        return assignedMember != null ? assignedMember.getFirstName() + " " + assignedMember.getLastName() : null;
    }
}
