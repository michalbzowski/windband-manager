package pl.michalbzowski.windband.domain.inventory;

/**
 * Priority level for inventory needs.
 */
public enum NeedPriority {
    LOW("Niska"),
    NORMAL("Normalna"),
    HIGH("Wysoka"),
    URGENT("Pilna");

    private final String displayName;

    NeedPriority(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}