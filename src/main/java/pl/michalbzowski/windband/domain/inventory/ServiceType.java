package pl.michalbzowski.windband.domain.inventory;

/**
 * Type of service/maintenance performed on an instrument.
 */
public enum ServiceType {
    /**
     * Regular scheduled maintenance (cleaning, oiling, adjustment).
     */
    MAINTENANCE("Konserwacja"),

    /**
     * Repair of broken/damaged parts.
     */
    REPAIR("Naprawa"),

    /**
     * Major overhaul / complete restoration.
     */
    OVERHAUL("Generalny remont"),

    /**
     * Cleaning only (no adjustments/repairs).
     */
    CLEANING("Czyszczenie"),

    /**
     * Calibration / tuning / intonation adjustment.
     */
    CALIBRATION("Kalibracja/Strojenie"),

    /**
     * Inspection / condition assessment.
     */
    INSPECTION("Przegląd"),

    /**
     * Modification / upgrade (e.g., adding trigger, changing leadpipe).
     */
    MODIFICATION("Modyfikacja"),

    /**
     * Emergency repair (urgent, unplanned).
     */
    EMERGENCY("Awaryjna");

    private final String displayName;

    ServiceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isPlanned() {
        return this != EMERGENCY;
    }
}