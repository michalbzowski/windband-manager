package pl.michalbzowski.windband.domain.inventory;

/**
 * Type of warehouse/storage location.
 */
public enum WarehouseType {
    /**
     * Internal warehouse managed by the band (room, cabinet, container).
     */
    INTERNAL("Wewnętrzny"),

    /**
     * External warehouse (another band, institution, storage facility).
     */
    EXTERNAL("Zewnętrzny"),

    /**
     * Archive/retired items storage.
     */
    ARCHIVE("Archiwum"),

    /**
     * Service/repair location (items currently being serviced).
     */
    SERVICE("Serwis"),

    /**
     * Temporary location (transit, staging).
     */
    TRANSIT("W transporcie");

    private final String displayName;

    WarehouseType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}