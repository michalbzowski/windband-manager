package pl.michalbzowski.windband.domain.inventory;

/**
 * Lifecycle status of an inventory item controlling availability.
 * - AVAILABLE: in stock, can be assigned to members
 * - RETIRED_FROM_STOCK: removed from active stock but still tracked (different counting)
 * - DISPOSED: physically destroyed/removed, cannot be assigned
 */
public enum ItemLifecycleStatus {
    AVAILABLE,
    RETIRED_FROM_STOCK,
    DISPOSED
}
