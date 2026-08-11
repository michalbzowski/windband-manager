package pl.michalbzowski.windband.domain.inventory;

/**
 * Data types for inventory item attributes.
 * Replaces the separate attribute type enums.
 */
public enum AttributeDataType {
    /**
     * Plain text input.
     */
    TEXT("Tekst"),

    /**
     * Long text / textarea.
     */
    TEXTAREA("Długi tekst"),

    /**
     * Integer number.
     */
    INTEGER("Liczba całkowita"),

    /**
     * Decimal number.
     */
    DECIMAL("Liczba dziesiętna"),

    /**
     * Boolean true/false.
     */
    BOOLEAN("Tak/Nie"),

    /**
     * Date picker.
     */
    DATE("Data"),

    /**
     * Single select from predefined options.
     */
    SELECT("Lista rozwijana"),

    /**
     * Multi select from predefined options.
     */
    MULTISELECT("Wielokrotny wybór"),

    /**
     * File upload reference.
     */
    FILE("Plik"),

    /**
     * Color picker.
     */
    COLOR("Kolor"),

    /**
     * Email address.
     */
    EMAIL("E-mail"),

    /**
     * URL link.
     */
    URL("Link");

    private final String displayName;

    AttributeDataType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTextLike() {
        return this == TEXT || this == TEXTAREA || this == EMAIL || this == URL;
    }

    public boolean isNumeric() {
        return this == INTEGER || this == DECIMAL;
    }

    public boolean isSelectable() {
        return this == SELECT || this == MULTISELECT;
    }
}