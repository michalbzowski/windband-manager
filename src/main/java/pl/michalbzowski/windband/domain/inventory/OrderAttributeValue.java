package pl.michalbzowski.windband.domain.inventory;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "order_attribute_values",
        uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "attribute_def_id"}))
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private InventoryOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_def_id", nullable = false)
    private OrderAttributeDef attributeDef;

    @Column(name = "value_text")
    private String value;

    private OrderAttributeValue(InventoryOrder order, OrderAttributeDef def, String value) {
        this.order = Objects.requireNonNull(order);
        this.attributeDef = Objects.requireNonNull(def);
        this.value = value;
    }

    public static OrderAttributeValue create(InventoryOrder order, OrderAttributeDef def, String value) {
        return new OrderAttributeValue(order, def, value);
    }

    public void setValue(String value) {
        this.value = value;
    }
}
