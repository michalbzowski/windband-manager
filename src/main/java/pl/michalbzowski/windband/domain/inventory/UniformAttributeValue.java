package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "uniform_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"uniform_item_id", "attribute_def_id"}))
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniformAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uniform_item_id", nullable = false)
    private UniformItem uniformItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private UniformAttributeDef attributeDef;

    @Column(name = "value_text")
    private String value;

    private UniformAttributeValue(UniformItem item, UniformAttributeDef def, String value) {
        this.uniformItem = Objects.requireNonNull(item);
        this.attributeDef = Objects.requireNonNull(def);
        this.value = value;
    }

    public static UniformAttributeValue create(UniformItem item, UniformAttributeDef def, String value) {
        return new UniformAttributeValue(item, def, value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
