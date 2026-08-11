package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.user.AppUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * History of item transfers between warehouses.
 * Provides full audit trail of item movements.
 */
@Entity
@Table(name = "warehouse_transfers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_warehouse_id")
    private Warehouse fromWarehouse; // null = no previous location (new item)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_by_user_id", nullable = false)
    private AppUser transferredBy;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "transfer_datetime", nullable = false)
    private LocalDateTime transferDateTime;

    private String reason; // e.g., "reorganization", "service", "loan to other band"

    private String notes;

    // Condition at transfer time
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_transfer")
    private ItemCondition conditionAtTransfer;

    // For service transfers - expected return date
    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    // Cost associated with transfer (shipping, service cost)
    @Column(name = "transfer_cost", precision = 12, scale = 2)
    private BigDecimal transferCost;

    @Column(name = "reference_number")
    private String referenceNumber; // e.g., shipping label, service ticket number

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    public WarehouseTransfer(InventoryItem item, Warehouse fromWarehouse, Warehouse toWarehouse,
                             AppUser transferredBy, String reason, Band band) {
        this.item = Objects.requireNonNull(item, "item required");
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = Objects.requireNonNull(toWarehouse, "toWarehouse required");
        this.transferredBy = Objects.requireNonNull(transferredBy, "transferredBy required");
        this.reason = reason;
        this.band = Objects.requireNonNull(band, "band required");
        this.transferDate = LocalDate.now();
        this.transferDateTime = LocalDateTime.now();
    }

    public static WarehouseTransfer createInitialPlacement(InventoryItem item, Warehouse warehouse,
                                                            AppUser user, Band band) {
        return new WarehouseTransfer(item, null, warehouse, user, "initial_placement", band);
    }

    public static WarehouseTransfer createServiceTransfer(InventoryItem item, Warehouse serviceWarehouse,
                                                           AppUser user, String reason,
                                                           LocalDate expectedReturnDate, Band band) {
        WarehouseTransfer transfer = new WarehouseTransfer(item, item.getLocation(), serviceWarehouse,
                user, reason != null ? reason : "service", band);
        transfer.setExpectedReturnDate(expectedReturnDate);
        return transfer;
    }
}