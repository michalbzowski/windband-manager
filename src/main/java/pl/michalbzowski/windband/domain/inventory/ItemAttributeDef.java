package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.michalbzowski.windband.domain.band.Band;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified attribute definition for all inventory item types.
 * Replaces UniformAttributeDef, InstrumentAttributeDef, AwardAttributeDef, OrderAttributeDef.
 * Each definition is scoped to an ItemType and optionally to a Band.
 */
@Entity
@Table(name = "item_attribute_defs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemAttributeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false)
    private AttributeDataType dataType;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id")
    private Band band; // null = global definition available to all bands

    // UI and validation
    @Column(name = "display_in_list")
    private boolean displayInList = false;

    @Column(name = "display_order")
    private int displayOrder = 0;

    @Column(name = "is_required")
    private boolean required = false;

    @Column(name = "is_filterable")
    private boolean filterable = true;

    @Column(name = "is_conditional")
    private boolean conditional = false;

    @Column(name = "depends_on_def_id")
    private Long dependsOnDefId;

    @Column(name = "conditional_value")
    private String conditionalValue; // value of parent that makes this visible

    @Column(columnDefinition = "TEXT")
    private String validationRegex;

    @Column(name = "validation_message")
    private String validationMessage;

    @Column(name = "default_value")
    private String defaultValue;

    // For SELECT/MULTISELECT types - predefined options (JSON array or semicolon-separated)
    @Column(name = "options", columnDefinition = "TEXT")
    private String options;

    // For conditional logic - parent attribute name (denormalized for easier queries)
    @Column(name = "depends_on_attribute")
    private String dependsOnAttribute;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "attributeDef", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemAttributeValue> values = new ArrayList<>();

    public ItemAttributeDef(String name, AttributeDataType dataType, ItemType itemType, Band band) {
        this.name = Objects.requireNonNull(name, "name required");
        this.dataType = Objects.requireNonNull(dataType, "dataType required");
        this.itemType = Objects.requireNonNull(itemType, "itemType required");
        this.band = band;
    }

    public static ItemAttributeDef createGlobal(String name, AttributeDataType dataType, ItemType itemType) {
        return new ItemAttributeDef(name, dataType, itemType, null);
    }

    public static ItemAttributeDef createBandScoped(String name, AttributeDataType dataType, ItemType itemType, Band band) {
        return new ItemAttributeDef(name, dataType, itemType, band);
    }

    public boolean isGlobal() {
        return band == null;
    }

    public boolean isSelectType() {
        return dataType == AttributeDataType.SELECT || dataType == AttributeDataType.MULTISELECT;
    }

    public List<String> getOptionsList() {
        if (options == null || options.isBlank()) return List.of();
        return List.of(options.split(";"));
    }

    public void setOptionsList(List<String> optionsList) {
        if (optionsList == null || optionsList.isEmpty()) {
            this.options = null;
        } else {
            this.options = String.join(";", optionsList);
        }
    }
}