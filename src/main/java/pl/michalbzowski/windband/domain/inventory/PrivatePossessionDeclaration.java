package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Declaration of private or external possession of an item.
 * Used when a member uses their own instrument/uniform, or when items are loaned
 * from another band/institution. Provides traceability without transferring ownership.
 */
@Entity
@Table(name = "private_possession_declarations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrivatePossessionDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member; // the member who possesses/declares the item

    @Enumerated(EnumType.STRING)
    @Column(name = "external_owner_type", nullable = false)
    private ExternalOwnerType externalOwnerType;

    @Column(name = "external_owner_name")
    private String externalOwnerName; // name of other band, institution, or "Private"

    @Column(name = "external_owner_contact")
    private String externalOwnerContact; // contact person, phone, email

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    private String brand;
    private String model;
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition")
    private ItemCondition condition;

    @Column(name = "declared_date", nullable = false)
    private LocalDate declaredDate;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    // Estimated value for insurance purposes
    @Column(name = "estimated_value", precision = 12, scale = 2)
    private BigDecimal estimatedValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Document references (photos, receipts, loan agreements)
    @Column(name = "document_paths", columnDefinition = "TEXT")
    private String documentPaths; // semicolon-separated paths/URLs

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declared_by_user_id", nullable = false)
    private AppUser declaredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private AppUser verifiedBy;

    @Column(name = "verified_at")
    private LocalDate verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeclarationStatus status = DeclarationStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    public PrivatePossessionDeclaration(Member member, ExternalOwnerType externalOwnerType,
                                        String itemName, ItemType itemType,
                                        AppUser declaredBy, Band band) {
        this.member = Objects.requireNonNull(member, "member required");
        this.externalOwnerType = Objects.requireNonNull(externalOwnerType, "externalOwnerType required");
        this.itemName = Objects.requireNonNull(itemName, "itemName required");
        this.itemType = Objects.requireNonNull(itemType, "itemType required");
        this.declaredBy = Objects.requireNonNull(declaredBy, "declaredBy required");
        this.band = Objects.requireNonNull(band, "band required");
        this.declaredDate = LocalDate.now();
        this.status = DeclarationStatus.ACTIVE;
        this.validFrom = LocalDate.now();
    }

    public void verify(AppUser verifier) {
        this.verifiedBy = Objects.requireNonNull(verifier);
        this.verifiedAt = LocalDate.now();
    }

    public void expire() {
        this.status = DeclarationStatus.EXPIRED;
    }

    public void revoke() {
        this.status = DeclarationStatus.REVOKED;
    }

    public boolean isActive() {
        return status == DeclarationStatus.ACTIVE &&
                (validUntil == null || !validUntil.isBefore(LocalDate.now()));
    }
}