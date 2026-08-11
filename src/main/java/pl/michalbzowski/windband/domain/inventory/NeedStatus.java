package pl.michalbzowski.windband.domain.inventory;

/**
 * Status of an inventory need/request.
 * Replaces and extends OrderStatus for the unified needs workflow.
 */
public enum NeedStatus {
    /**
     * Need has been reported, awaiting review.
     */
    NEW("Nowe"),

    /**
     * Need forwarded for approval (to manager/board).
     */
    FORWARDED_TO_APPROVAL("Przekazane do akceptacji"),

    /**
     * Need approved, ready for procurement.
     */
    APPROVED("Zaakceptowane"),

    /**
     * Need rejected.
     */
    REJECTED("Odrzucone"),

    /**
     * Order placed with supplier.
     */
    ORDERED("Zamówione"),

    /**
     * In procurement/production process.
     */
    IN_PROGRESS("W realizacji"),

    /**
     * Items delivered to warehouse.
     */
    DELIVERED("Dostarczone"),

    /**
     * Need fully satisfied and closed.
     */
    COMPLETED("Zakończone"),

    /**
     * Need cancelled before completion.
     */
    CANCELLED("Anulowane");

    private final String displayName;

    NeedStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED;
    }

    public boolean canTransitionTo(NeedStatus next) {
        return switch (this) {
            case NEW -> next == FORWARDED_TO_APPROVAL || next == REJECTED || next == CANCELLED;
            case FORWARDED_TO_APPROVAL -> next == APPROVED || next == REJECTED || next == CANCELLED;
            case APPROVED -> next == ORDERED || next == CANCELLED;
            case ORDERED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == DELIVERED || next == CANCELLED;
            case DELIVERED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED, REJECTED -> false;
        };
    }
}