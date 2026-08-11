package pl.michalbzowski.windband.domain.inventory;

/**
 * Priority level for instrument service.
 */
public enum ServicePriority {
    LOW("Niska"),
    NORMAL("Normalna"),
    HIGH("Wysoka"),
    URGENT("Pilna");

    private final String displayName;

    ServicePriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}