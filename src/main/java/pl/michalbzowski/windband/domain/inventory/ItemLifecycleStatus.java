package pl.michalbzowski.windband.domain.inventory;

/**
 * Lifecycle status of an inventory item controlling availability.
 * - AVAILABLE: in stock, can be assigned to members
 * - ASSIGNED: currently assigned to a member
 * - IN_SERVICE: sent for maintenance/repair
 * - RETIRED_FROM_STOCK: removed from active stock but still tracked (different counting)
 * - DISPOSED: physically destroyed/removed, cannot be assigned
 * - LOST: reported lost, investigation pending
 */
public enum ItemLifecycleStatus {
    AVAILABLE,
    ASSIGNED,
    IN_SERVICE,
    RETIRED_FROM_STOCK,
    DISPOSED,
    LOST
}
