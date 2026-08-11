package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Base entity for all inventory items. Uses single-table inheritance (SINGLE_TABLE)
 * with a discriminator column (item_type) to store all item types in one table.
 * This enables unified queries, reporting, and polymorphic associations.
 */
@Entity
@Table(name = "inventory_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "item_type", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", insertable = false, updatable = false, nullable = false)
    private ItemType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member assignedMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_status", nullable = false)
    private OwnershipStatus ownershipStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private ItemLifecycleStatus lifecycleStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(name = "order_number")
    private String orderNumber;

    // System identification
    @Column(name = "system_id", unique = true)
    private String systemId;

    @Column(name = "external_inventory_number")
    private String externalInventoryNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_owner_type")
    private ExternalOwnerType externalOwnerType;

    @Column(name = "external_owner_name")
    private String externalOwnerName;

    // Physical characteristics
    private String serialNumber;
    private String manufacturer;
    private String model;

    // Financial / procurement
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 12, scale = 2)
    private BigDecimal purchaseCost;

    // Condition & notes
    @Enumerated(EnumType.STRING)
    @Column(name = "condition")
    private ItemCondition condition;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Unit of measure (for consumables)
    private String unit;

    // Location
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse location;

    // Source need tracking (for items created from inventory needs)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_need_id")
    private InventoryNeed sourceNeed;

    protected InventoryItem(String name, ItemType type, OwnershipStatus ownershipStatus, Band band) {
        this.name = Objects.requireNonNull(name, "name required");
        this.type = Objects.requireNonNull(type, "type required");
        this.ownershipStatus = Objects.requireNonNull(ownershipStatus, "ownershipStatus required");
        this.band = Objects.requireNonNull(band, "band required");
        this.lifecycleStatus = ItemLifecycleStatus.AVAILABLE;
        this.condition = ItemCondition.GOOD;
    }

    public void assignTo(Member member) {
        if (lifecycleStatus == ItemLifecycleStatus.DISPOSED || lifecycleStatus == ItemLifecycleStatus.LOST) {
            throw new IllegalStateException("Cannot assign item in status: " + lifecycleStatus);
        }
        this.assignedMember = member;
        this.lifecycleStatus = ItemLifecycleStatus.ASSIGNED;
    }

    public void unassign() {
        this.assignedMember = null;
        if (lifecycleStatus == ItemLifecycleStatus.ASSIGNED) {
            this.lifecycleStatus = ItemLifecycleStatus.AVAILABLE;
        }
    }

    public void updateOwnershipStatus(OwnershipStatus status) {
        this.ownershipStatus = Objects.requireNonNull(status);
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public void retireFromStock() {
        this.lifecycleStatus = ItemLifecycleStatus.RETIRED_FROM_STOCK;
    }

    public void dispose() {
        if (assignedMember != null) {
            throw new IllegalStateException("Cannot dispose item that is assigned to a member: " + id);
        }
        this.lifecycleStatus = ItemLifecycleStatus.DISPOSED;
    }

    public void markLost() {
        if (assignedMember != null) {
            throw new IllegalStateException("Cannot mark item as lost while assigned. Return it first.");
        }
        this.lifecycleStatus = ItemLifecycleStatus.LOST;
    }

    public void sendToService() {
        if (assignedMember != null) {
            throw new IllegalStateException("Cannot send assigned item to service. Return it first.");
        }
        this.lifecycleStatus = ItemLifecycleStatus.IN_SERVICE;
    }

    public void returnFromService() {
        this.lifecycleStatus = ItemLifecycleStatus.AVAILABLE;
    }

    public boolean isAvailable() {
        return lifecycleStatus == ItemLifecycleStatus.AVAILABLE;
    }

    public boolean isAssigned() {
        return assignedMember != null;
    }

    public boolean isInService() {
        return lifecycleStatus == ItemLifecycleStatus.IN_SERVICE;
    }

    @Transient
    public String getAssignedMemberName() {
        return assignedMember != null ? assignedMember.getFirstName() + " " + assignedMember.getLastName() : null;
    }

    @Transient
    public boolean isExternal() {
        return ownershipStatus == OwnershipStatus.EXTERNAL || ownershipStatus == OwnershipStatus.PRIVATE;
    }
}