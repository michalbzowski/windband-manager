package pl.michalbzowski.windband.domain.inventory;

/**
 * Physical condition of an inventory item.
 */
public enum ItemCondition {
    /**
     * New, unused, perfect condition.
     */
    NEW("Nowy"),

    /**
     * Good condition, fully functional, minor wear.
     */
    GOOD("Dobry"),

    /**
     * Fair condition, functional but shows wear, may need maintenance soon.
     */
    FAIR("Przeciętny"),

    /**
     * Poor condition, needs repair, limited functionality.
     */
    POOR("Słaby"),

    /**
     * Damaged, not functional, requires significant repair or disposal.
     */
    DAMAGED("Uszkodzony");

    private final String displayName;

    ItemCondition(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}