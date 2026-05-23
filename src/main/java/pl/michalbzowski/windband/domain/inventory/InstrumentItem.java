package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.util.Objects;

@Entity
@Table(name = "instrument_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String brand;
    private String serialNumber;
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member assignedMember;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OwnershipStatus ownershipStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    private InstrumentItem(String name, OwnershipStatus ownershipStatus, Band band) {
        this.name = Objects.requireNonNull(name, "name required");
        this.ownershipStatus = Objects.requireNonNull(ownershipStatus, "ownershipStatus required");
        this.band = Objects.requireNonNull(band, "band required");
    }

    public static InstrumentItem createOwned(String name, Band band) {
        return new InstrumentItem(name, OwnershipStatus.OWNED, band);
    }

    public static InstrumentItem createBorrowed(String name, Band band) {
        return new InstrumentItem(name, OwnershipStatus.BORROWED, band);
    }

    public static InstrumentItem createMissing(String name, Band band) {
        return new InstrumentItem(name, OwnershipStatus.MISSING, band);
    }

    public void assignTo(Member member) {
        this.assignedMember = member;
    }

    public void unassign() {
        this.assignedMember = null;
    }

    public void updateOwnershipStatus(OwnershipStatus status) {
        this.ownershipStatus = Objects.requireNonNull(status);
    }

    public void updateDetails(String brand, String serialNumber, String description) {
        this.brand = brand;
        this.serialNumber = serialNumber;
        this.description = description;
    }
}
