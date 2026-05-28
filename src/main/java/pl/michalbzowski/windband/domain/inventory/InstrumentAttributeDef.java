package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import pl.michalbzowski.windband.domain.band.Band;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "instrument_attribute_defs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"band_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentAttributeDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id", nullable = false)
    private Band band;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_in_list", nullable = false)
    private boolean displayInList = false;

    @Column(nullable = false)
    private LocalDate createdAt;

    @Column(length = 2000)
    private String options;

    // Conditional display: this attribute is shown only when dependsOnAttribute has dependsOnValue
    @Column(name = "depends_on_attribute_id")
    private Long dependsOnAttributeId;

    @Column(name = "depends_on_value")
    private String dependsOnValue; // comma-separated values when this attribute is visible

    private InstrumentAttributeDef(Band band, String name, String type, boolean required, int displayOrder, String options, boolean displayInList) {
        this.band = Objects.requireNonNull(band);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.required = required;
        this.displayOrder = displayOrder;
        this.options = options;
        this.active = true;
        this.displayInList = displayInList;
        this.createdAt = LocalDate.now();
    }

    public static InstrumentAttributeDef create(Band band, String name, String type, boolean required, int displayOrder, String options) {
        return new InstrumentAttributeDef(band, name, type, required, displayOrder, options, false);
    }

    public static InstrumentAttributeDef create(Band band, String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        return new InstrumentAttributeDef(band, name, type, required, displayOrder, options, displayInList);
    }

    public void update(String name, String type, boolean required, boolean displayInList, int displayOrder, String options) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.required = required;
        this.displayInList = displayInList;
        this.displayOrder = displayOrder;
        this.options = options;
    }

    /**
     * Check if this attribute should be displayed based on current form values.
     * @param parentAttributeValue the value of the parent attribute (from dependsOnAttributeId)
     * @return true if this attribute should be shown
     */
    public boolean isVisible(String parentAttributeValue) {
        if (dependsOnAttributeId == null || dependsOnValue == null || dependsOnValue.isBlank()) {
            return true;
        }
        if (parentAttributeValue == null || parentAttributeValue.isBlank()) {
            return false;
        }
        String[] allowedValues = dependsOnValue.split(",");
        for (String allowed : allowedValues) {
            if (parentAttributeValue.equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }
}
