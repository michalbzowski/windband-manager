package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.member.Member;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * InventoryOrder represents a custom order placed by a band member
 * for a uniform item or instrument that is not yet in stock.
 * Tracks the full lifecycle from submission to delivery.
 */
@Entity
@Table(name = "inventory_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member requester;

    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryOrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String notes;

    @Column(columnDefinition = "TEXT")
    private String attributesJson;

    private InventoryOrder(Member requester, InventoryOrderType orderType) {
        this.requester = Objects.requireNonNull(requester, "requester required");
        this.orderType = Objects.requireNonNull(orderType, "orderType required");
        this.status = OrderStatus.SUBMITTED;
        this.createdAt = LocalDateTime.now();
    }

    public static InventoryOrder place(Member requester, InventoryOrderType orderType) {
        return new InventoryOrder(requester, orderType);
    }

    public void advanceToApproval() {
        this.status = OrderStatus.PENDING_APPROVAL;
        this.updatedAt = LocalDateTime.now();
    }

    public void advanceToProduction() {
        this.status = OrderStatus.IN_PRODUCTION;
        this.updatedAt = LocalDateTime.now();
    }

    public void markShipped() {
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markDelivered() {
        this.status = OrderStatus.DELIVERED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void addNotes(String notes) {
        this.notes = notes;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void generateOrderNumber() {
        this.orderNumber = "ORD-" + id + "-" + createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    public void setAttributesJson(String attributesJson) {
        this.attributesJson = attributesJson;
    }
}
