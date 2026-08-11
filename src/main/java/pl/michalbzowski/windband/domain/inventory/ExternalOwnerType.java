package pl.michalbzowski.windband.domain.inventory;

/**
 * Type of external owner for items not owned by the band.
 */
public enum ExternalOwnerType {
    /**
     * Privately owned by a member (member's personal property).
     */
    PRIVATE("Prywatne"),

    /**
     * Owned by another band/orchestra.
     */
    OTHER_BAND("Inny zespół"),

    /**
     * Owned by another institution (school, cultural center, etc.).
     */
    OTHER_INSTITUTION("Inna instytucja"),

    /**
     * Owner unknown or not specified.
     */
    UNKNOWN("Nieznane");

    private final String displayName;

    ExternalOwnerType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}