package pl.michalbzowski.windband.domain.inventory;

/**
 * Status of an instrument service record.
 */
public enum ServiceStatus {
    /**
     * Service scheduled, awaiting approval.
     */
    SCHEDULED("Zaplanowana"),

    /**
     * Approved, ready to start.
     */
    APPROVED("Zatwierdzona"),

    /**
     * Currently in progress at service provider.
     */
    IN_PROGRESS("W realizacji"),

    /**
     * Completed successfully.
     */
    COMPLETED("Zakończona"),

    /**
     * Cancelled before/during service.
     */
    CANCELLED("Anulowana");

    private final String displayName;

    ServiceStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return this == SCHEDULED || this == APPROVED || this == IN_PROGRESS;
    }
}