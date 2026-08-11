package pl.michalbzowski.windband.domain.inventory;

/**
 * Unified type of inventory items. Replaces the need for separate entity hierarchies.
 * Each type has its own set of specific attributes defined via ItemAttributeDef.
 */
public enum ItemType {
    /**
     * Uniforms, clothing, headgear, shoes, accessories worn by members.
     */
    UNIFORM("Strój", "uniform"),

    /**
     * Musical instruments: wind, percussion, string, electronic, accessories.
     */
    INSTRUMENT("Instrument", "instrument"),

    /**
     * Awards, decorations, medals, diplomas, honors given to members.
     */
    AWARD("Odznaka/Nagroda", "award"),

    /**
     * Sheet music, method books, scores, parts.
     */
    SHEET_MUSIC("Nuty/Literatura", "sheet_music"),

    /**
     * Equipment: stands, cases, metronomes, tuners, recording gear, PA.
     */
    EQUIPMENT("Sprzęt", "equipment"),

    /**
     * Consumables: reeds, valve oil, cork grease, drumsticks, cleaning kits.
     */
    CONSUMABLE("Środek eksploatacyjny", "consumable"),

    /**
     * Furniture: chairs, music stands, cabinets, risers, podiums.
     */
    FURNITURE("Mebel", "furniture"),

    /**
     * Other items not fitting above categories.
     */
    OTHER("Inne", "other");

    private final String displayName;
    private final String code;

    ItemType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static ItemType fromCode(String code) {
        for (ItemType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ItemType code: " + code);
    }
}