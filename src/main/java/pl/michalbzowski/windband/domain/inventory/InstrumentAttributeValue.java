package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "instrument_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"instrument_item_id", "attribute_def_id"}))
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstrumentAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_item_id", nullable = false)
    private InstrumentItem instrumentItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private InstrumentAttributeDef attributeDef;

    @Column(name = "value_text")
    private String value;

    private InstrumentAttributeValue(InstrumentItem item, InstrumentAttributeDef def, String value) {
        this.instrumentItem = Objects.requireNonNull(item);
        this.attributeDef = Objects.requireNonNull(def);
        this.value = value;
    }

    public static InstrumentAttributeValue create(InstrumentItem item, InstrumentAttributeDef def, String value) {
        return new InstrumentAttributeValue(item, def, value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
