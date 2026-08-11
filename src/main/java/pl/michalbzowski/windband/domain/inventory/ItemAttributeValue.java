package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * Unified attribute value for inventory items.
 * Replaces UniformAttributeValue, InstrumentAttributeValue, AwardAttributeValue, OrderAttributeValue.
 * Links an InventoryItem to an ItemAttributeDef with a typed value.
 */
@Entity
@Table(name = "item_attribute_values")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private ItemAttributeDef attributeDef;

    // Value stored as text - actual type determined by attributeDef.dataType
    @Column(columnDefinition = "TEXT")
    private String value;

    // For numeric values - denormalized for easier querying/sorting
    private Long valueInteger;

    private Double valueDecimal;

    // For boolean values - denormalized
    private Boolean valueBoolean;

    // For date values - denormalized
    private java.time.LocalDate valueDate;

    // For file values - path/reference
    @Column(name = "value_file_path")
    private String valueFilePath;

    public ItemAttributeValue(InventoryItem item, ItemAttributeDef attributeDef, String value) {
        this.item = Objects.requireNonNull(item, "item required");
        this.attributeDef = Objects.requireNonNull(attributeDef, "attributeDef required");
        this.value = value;
        parseAndSetTypedValue(value, attributeDef.getDataType());
    }

    public void setValue(String value) {
        this.value = value;
        parseAndSetTypedValue(value, attributeDef != null ? attributeDef.getDataType() : null);
    }

    private void parseAndSetTypedValue(String rawValue, AttributeDataType dataType) {
        // Reset all typed values
        this.valueInteger = null;
        this.valueDecimal = null;
        this.valueBoolean = null;
        this.valueDate = null;
        this.valueFilePath = null;

        if (rawValue == null || rawValue.isBlank()) return;

        try {
            switch (dataType) {
                case INTEGER -> this.valueInteger = Long.parseLong(rawValue);
                case DECIMAL -> this.valueDecimal = Double.parseDouble(rawValue);
                case BOOLEAN -> this.valueBoolean = Boolean.parseBoolean(rawValue);
                case DATE -> this.valueDate = java.time.LocalDate.parse(rawValue);
                case FILE -> this.valueFilePath = rawValue;
                default -> {} // TEXT, TEXTAREA, SELECT, MULTISELECT, EMAIL, URL, COLOR stay as string
            }
        } catch (Exception e) {
            // Keep only string value if parsing fails
        }
    }

    public Object getTypedValue() {
        if (attributeDef == null) return value;
        return switch (attributeDef.getDataType()) {
            case INTEGER -> valueInteger;
            case DECIMAL -> valueDecimal;
            case BOOLEAN -> valueBoolean;
            case DATE -> valueDate;
            case FILE -> valueFilePath;
            default -> value;
        };
    }

    public boolean hasValue() {
        return value != null && !value.isBlank();
    }
}