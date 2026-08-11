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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified need/request for inventory items.
 * Replaces and extends InventoryOrder - covers the full lifecycle from need reporting
 * through procurement to delivery and inventory registration.
 * Can be for any ItemType, not just uniforms/instruments.
 */
@Entity
@Table(name = "inventory_needs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryNeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "item_name", nullable = false)
    private String itemName; // e.g., "Trąbka B♭", "Mundurek koncertowy"

    @Column(name = "item_description", columnDefinition = "TEXT")
    private String itemDescription;

    // Desired specifications (for procurement)
    private String desiredBrand;
    private String desiredModel;
    private String desiredSize; // for uniforms
    private String desiredColor;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    @Column(name = "estimated_unit_cost", precision = 12, scale = 2)
    private BigDecimal estimatedUnitCost;

    @Column(name = "estimated_total_cost", precision = 12, scale = 2)
    private BigDecimal estimatedTotalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private NeedPriority priority = NeedPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NeedStatus status = NeedStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_member_id", nullable = false)
    private Member requestedBy; // member who needs the item

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private AppUser requestedByUser; // user who submitted the request

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private AppUser approvedByUser;

    @Column(name = "approved_at")
    private LocalDate approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // Preferred supplier info
    @Column(name = "preferred_supplier")
    private String preferredSupplier;

    @Column(name = "supplier_contact")
    private String supplierContact;

    @Column(name = "supplier_quote_reference")
    private String supplierQuoteReference;

    @Column(name = "order_number")
    private String orderNumber; // PO number when ordered

    @Column(name = "ordered_at")
    private LocalDate orderedAt;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "delivered_at")
    private LocalDate deliveredAt;

    @Column(name = "completed_at")
    private LocalDate completedAt;

    @Column(name = "cancelled_at")
    private LocalDate cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Link to created inventory items upon delivery
    @OneToMany(mappedBy = "sourceNeed", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InventoryItem> createdItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    // For conditional attributes - store as JSON or semicolon-separated
    @Column(name = "attribute_values", columnDefinition = "TEXT")
    private String attributeValues;

    public InventoryNeed(ItemType itemType, String itemName, Member requestedBy,
                         AppUser requestedByUser, Band band) {
        this.itemType = Objects.requireNonNull(itemType, "itemType required");
        this.itemName = Objects.requireNonNull(itemName, "itemName required");
        this.requestedBy = Objects.requireNonNull(requestedBy, "requestedBy required");
        this.requestedByUser = Objects.requireNonNull(requestedByUser, "requestedByUser required");
        this.band = Objects.requireNonNull(band, "band required");
        this.status = NeedStatus.NEW;
    }

    public static InventoryNeed create(Member requestedBy, AppUser requestedByUser,
                                       ItemType itemType, String itemName,
                                       int quantity, Band band) {
        InventoryNeed need = new InventoryNeed(itemType, itemName, requestedBy, requestedByUser, band);
        need.quantity = quantity;
        return need;
    }

    public void forwardToApproval(AppUser approver) {
        if (status != NeedStatus.NEW) {
            throw new IllegalStateException("Can only forward NEW needs to approval");
        }
        this.status = NeedStatus.FORWARDED_TO_APPROVAL;
    }

    public void approve(AppUser approver) {
        if (!status.canTransitionTo(NeedStatus.APPROVED)) {
            throw new IllegalStateException("Cannot approve from status: " + status);
        }
        this.status = NeedStatus.APPROVED;
        this.approvedByUser = Objects.requireNonNull(approver);
        this.approvedAt = LocalDate.now();
    }

    public void reject(AppUser rejecter, String reason) {
        if (!status.canTransitionTo(NeedStatus.REJECTED)) {
            throw new IllegalStateException("Cannot reject from status: " + status);
        }
        this.status = NeedStatus.REJECTED;
        this.rejectionReason = Objects.requireNonNull(reason);
    }

    public void placeOrder(String orderNumber, String supplier, LocalDate expectedDelivery) {
        if (status != NeedStatus.APPROVED) {
            throw new IllegalStateException("Can only order APPROVED needs");
        }
        this.status = NeedStatus.ORDERED;
        this.orderNumber = orderNumber;
        this.preferredSupplier = supplier;
        this.orderedAt = LocalDate.now();
        this.expectedDeliveryDate = expectedDelivery;
    }

    public void markInProgress() {
        if (status != NeedStatus.ORDERED) {
            throw new IllegalStateException("Can only mark IN_PROGRESS from ORDERED");
        }
        this.status = NeedStatus.IN_PROGRESS;
    }

    public void markDelivered(LocalDate deliveryDate) {
        if (status != NeedStatus.ORDERED && status != NeedStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only mark DELIVERED from ORDERED or IN_PROGRESS");
        }
        this.status = NeedStatus.DELIVERED;
        this.deliveredAt = deliveryDate != null ? deliveryDate : LocalDate.now();
    }

    public void complete() {
        if (status != NeedStatus.DELIVERED) {
            throw new IllegalStateException("Can only complete DELIVERED needs");
        }
        this.status = NeedStatus.COMPLETED;
        this.completedAt = LocalDate.now();
    }

    public void cancel(String reason) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot cancel terminal status: " + status);
        }
        this.status = NeedStatus.CANCELLED;
        this.cancelledAt = LocalDate.now();
        this.cancellationReason = reason;
    }

    public void addCreatedItem(InventoryItem item) {
        createdItems.add(item);
        item.setSourceNeed(this); // back-reference if needed
    }

    public BigDecimal getEstimatedTotalCost() {
        if (estimatedUnitCost != null && quantity > 0) {
            return estimatedUnitCost.multiply(BigDecimal.valueOf(quantity));
        }
        return estimatedTotalCost;
    }
}