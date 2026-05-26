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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemLifecycleStatus lifecycleStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    private InstrumentItem(String name, OwnershipStatus ownershipStatus, Band band) {
        this.name = Objects.requireNonNull(name, "name required");
        this.ownershipStatus = Objects.requireNonNull(ownershipStatus, "ownershipStatus required");
        this.band = Objects.requireNonNull(band, "band required");
        this.lifecycleStatus = ItemLifecycleStatus.AVAILABLE;
    }

    public static InstrumentItem createOwned(String name, Band band) {
        return new InstrumentItem(name, OwnershipStatus.OWNED, band);
    }

    public static InstrumentItem createBorrowed(String name, Band band) {
        return new InstrumentItem(name, OwnershipStatus.BORROWED, band);
    }

    public void assignTo(Member member) {
        if (lifecycleStatus == ItemLifecycleStatus.DISPOSED) {
            throw new IllegalStateException("Cannot assign disposed instrument: " + id);
        }
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

    public void retireFromStock() {
        this.lifecycleStatus = ItemLifecycleStatus.RETIRED_FROM_STOCK;
    }

    public void dispose() {
        if (assignedMember != null) {
            throw new IllegalStateException("Cannot dispose instrument that is assigned to a member: " + id);
        }
        this.lifecycleStatus = ItemLifecycleStatus.DISPOSED;
    }

    public boolean isAvailable() {
        return lifecycleStatus == ItemLifecycleStatus.AVAILABLE;
    }

    public boolean isAssigned() {
        return assignedMember != null;
    }
}
